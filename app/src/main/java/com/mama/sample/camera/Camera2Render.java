package com.mama.sample.camera;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import androidx.annotation.NonNull;

import com.mama.sample.lib.NativeLib;
import com.mama.sample.utils.OrientationUtils;
import com.mama.sample.utils.YuvUtils;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class Camera2Render implements GLSurfaceView.Renderer {

    private Context mContext;
    private final static String TAG = "Camera2Render";
    private long mFrameTime;
    private int mTmpFPS;
    private int mFPS = 0;

    private int mPreviewWidth = 1280;
    private int mPreviewHeight = 720;

    private boolean mFrontCamera;
    private boolean mSwitchingCamera = false;

    private final Object mSyncObject = new Object();
    private final Object mFrameSyncObject = new Object();
    private boolean mPreviewChange = true;

    private int mProcessInterval;
    private RenderListener mRenderListener;
    private boolean destroy = false;

    private ProcessThread processThread;
    private Frame curFrame;
    private boolean mShowOriginal;

    class Frame {
        byte[] rgba;
        int width;
        int height;

        public Frame(byte[] rgba, int width, int height) {
            this.rgba = rgba;
            this.width = width;
            this.height = height;
        }
    }

    public interface RenderListener {
        void onSurfaceCreated();
    }

    public Camera2Render(Context context, @NonNull RenderListener renderListener) {
        mContext = context;
        mRenderListener = renderListener;
        destroy = false;

        processThread = new ProcessThread();
        processThread.start();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
//        initTextures();
        mRenderListener.onSurfaceCreated();
    }
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 arg0) {
        if (destroy) {
            NativeLib.destroyRender();
            return;
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glClearColor(0f, 0f, 0f, 1f);

        if (mPreviewChange) {
            synchronized (mSyncObject) {
                NativeLib.initRender(mPreviewWidth, mPreviewHeight);
                mPreviewChange = false;
            }
        }

        if (curFrame == null) return;

        synchronized (mFrameSyncObject) {
            NativeLib.renderToScreen(curFrame.rgba, curFrame.width, curFrame.height, mFrontCamera);
        }
        Log.e(TAG, "onDrawFrame 2222222222 w=" + curFrame.width + ", h=" + curFrame.height);
    }

    public void destroy() {
        NativeLib.destroyRender();
        destroy = true;
        processThread.release();
    }

    // Thread
    class ProcessThread extends Thread {
        final Object syncObject = new Object();
        private boolean isRunning;

        byte[] copyBuffer;
        int width;
        int height;
        boolean frontCamera;

        @Override
        public synchronized void start() {
            isRunning = true;
            super.start();
        }
        @Override
        public void run() {
            try {
                while (isRunning && !isInterrupted()) {
                    synchronized (syncObject) {
                        syncObject.wait();

                        if (!isRunning) break;

                        // do sdk process
                        byte[] rgba = null;
                        synchronized (mFrameSyncObject) {
                            OrientationUtils.FaceOrientation faceOrientation = OrientationUtils.getFaceOrientation(frontCamera);
                            rgba = YuvUtils.nv21ToRGBA(copyBuffer, width, height);
                            curFrame = new Frame(rgba, width, height);
                        }

                        if (System.currentTimeMillis() - mFrameTime >= 1000) {
                            mFPS = mTmpFPS;
                            mTmpFPS = 0;
                            mFrameTime = System.currentTimeMillis();
                        } else {
                            mTmpFPS ++;
                        }
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                isRunning = false;
            }
        }
        public void updateBuffer(byte[] nv21, int width, int height, boolean frontCamera) {
            this.width = width;
            this.height = height;
            this.frontCamera = frontCamera;
            if (copyBuffer == null || nv21.length != copyBuffer.length) {
                copyBuffer = new byte[nv21.length];
            }
            synchronized (syncObject) {
                System.arraycopy(nv21, 0, copyBuffer, 0, nv21.length);
                syncObject.notify();
            }
        }
        public synchronized void release() {
            isRunning = false;
            interrupt();
        }
    }

    public void updateFrame(byte[] nv21, int width, int height, boolean frontCamera) {
        if (mPreviewWidth != width || mPreviewHeight != height) {
            mPreviewWidth  = width;
            mPreviewHeight = height;
            mPreviewChange = true;
        }
        mFrontCamera = frontCamera;

        processThread.updateBuffer(nv21, width, height, frontCamera);
    }

    public void switchCamera() {
        mSwitchingCamera = true;
    }

    public void finishSwitchCamera() {
        mSwitchingCamera = false;
    }
    public int getFPS() {
        return mFPS;
    }

    public void showOriginal(boolean value) {
        mShowOriginal = value;
    }

    public int getPreviewHeight() {
        return mPreviewHeight;
    }
    public int getPreviewWidth() {
        return mPreviewWidth;
    }
    public boolean isSwitchingCamera() {
        return mSwitchingCamera;
    }
}
