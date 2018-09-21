package com.github.piasy.openglestutorial_android;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class MainActivity extends AppCompatActivity {

    private GLSurfaceView mGLSurfaceView;
    private MyRenderer mRenderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!Utils.supportGlEs20(this)) {
            Toast.makeText(this, "GLES 2.0 not supported!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mGLSurfaceView = (GLSurfaceView) findViewById(R.id.surface);

        mGLSurfaceView.setEGLContextClientVersion(2);//GL ES 版本
        mGLSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        mRenderer = new MyRenderer(this);
        mGLSurfaceView.setRenderer(mRenderer);
        //RENDERMODE_WHEN_DIRTY 懒惰渲染，需要手动调用 glSurfaceView.requestRender() 才会进行更新
        //RENDERMODE_CONTINUOUSLY 不停渲染
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGLSurfaceView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGLSurfaceView.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRenderer.destroy();
    }

    private static class MyRenderer implements GLSurfaceView.Renderer {

        private static final String VERTEX_SHADER =
                "uniform mat4 uMVPMatrix;" +
                "attribute vec4 vPosition;" +
                "attribute vec2 a_texCoord;" +
                "varying vec2 v_texCoord;" +
                "void main() {" +
                "  gl_Position = uMVPMatrix * vPosition;" +
                "  v_texCoord = a_texCoord;" +
                "}";
        private static final String FRAGMENT_SHADER =
                "precision mediump float;" +
                "varying vec2 v_texCoord;" +
                "uniform sampler2D s_texture;" +
                "void main() {" +
                "  gl_FragColor = texture2D(s_texture, v_texCoord);" +
                "}";
//初始值
//        1, 1, 0,   // top right
//                -1, 1, 0,  // top left
//                -1, -1, 0, // bottom left
//                1, -1, 0,  // bottom right

        //顶点坐标，可以理解为纹理的画布
        private static final float[] VERTEX = {   // in counterclockwise order:
                1f, 1f, 0,   // top right
                -1f, 1f, 0,  // top left
                -1f, -1f, 0, // bottom left
                1f, -1f, 0,  // bottom right
        };
        private static final short[] VERTEX_INDEX = {
                0, 1, 2, 0, 2, 3
        };

        //右下
//        1, 0.5f,  // bottom right
//        0.5f, 0.5f,  // bottom left
//        0.5f, 1,  // top left
//        1, 1,  // top right


//  左上
//        0.5f, 0,  // bottom right
//        0, 0,  // bottom left
//        0f, 0.5f,  // top left
//        0.5f, 0.5f,  // top right

//        右上
//        1, 0,  // bottom right
//        0.5f, 0f,  // bottom left
//        0.5f, 0.5f,  // top left
//        1f, 0.5f,  // top right

        //完整图
//        1, 0,  // top right
//        0f, 0f,  // top left
//        0f, 1f,  // bottom left
//        1f, 1f,  // bottom right

        //纹理坐标
        private static final float[] TEX_VERTEX = {   // in clockwise order:
                1f, 0,  // top right
                0f, 0f,  // top left
                0f, 1f,  // bottom left
                1f, 1f,  // bottom right
        };

        private final Context mContext;
        private final FloatBuffer mVertexBuffer;
        private final FloatBuffer mTexVertexBuffer;
        private final ShortBuffer mVertexIndexBuffer;
        private final float[] mMVPMatrix = new float[16];

        private int mProgram;
        private int mPositionHandle;
        private int mMatrixHandle;
        private int mTexCoordHandle;
        private int mTexSamplerHandle;
        private int mTexName;

        MyRenderer(final Context context) {
            mContext = context;
            mVertexBuffer = ByteBuffer.allocateDirect(VERTEX.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .put(VERTEX);
            mVertexBuffer.position(0);

            mVertexIndexBuffer = ByteBuffer.allocateDirect(VERTEX_INDEX.length * 2)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer()
                    .put(VERTEX_INDEX);
            mVertexIndexBuffer.position(0);

            mTexVertexBuffer = ByteBuffer.allocateDirect(TEX_VERTEX.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .put(TEX_VERTEX);
            mTexVertexBuffer.position(0);
        }

        //加载 shader 代码
        static int loadShader(int type, String shaderCode) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, shaderCode);
            GLES20.glCompileShader(shader);
            return shader;
        }

        @Override
        // surface 创建时被回调，通常用于进行初始化工作，只会被回调一次
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

            mProgram = GLES20.glCreateProgram();
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            GLES20.glAttachShader(mProgram, vertexShader);
            GLES20.glAttachShader(mProgram, fragmentShader);
            //链接 GLSL 程序
            GLES20.glLinkProgram(mProgram);

            //使用 GLSL 程序
            GLES20.glUseProgram(mProgram);

            //获取 vPosition shader 代码中的变量索引
            mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
            //获取 a_texCoord shader 代码中的变量索引
            mTexCoordHandle = GLES20.glGetAttribLocation(mProgram, "a_texCoord");
            mMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
            mTexSamplerHandle = GLES20.glGetUniformLocation(mProgram, "s_texture");

            //启用 vertex
            GLES20.glEnableVertexAttribArray(mPositionHandle);
            //绑定 vertex 坐标值
            GLES20.glVertexAttribPointer(mPositionHandle, 3, GLES20.GL_FLOAT, false,
                    12, mVertexBuffer);

            //启用 vertex
            GLES20.glEnableVertexAttribArray(mTexCoordHandle);
            //绑定 vertex 坐标值
            GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0,
                    mTexVertexBuffer);

            int[] texNames = new int[1];
            GLES20.glGenTextures(1, texNames, 0);
            mTexName = texNames[0];
            Bitmap bitmap = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.p_300px);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexName);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_REPEAT);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            bitmap.recycle();
        }

        @Override
        //在每次 surface 尺寸变化时被回调，注意，第一次得知 surface 的尺寸时也会回调
        public void onSurfaceChanged(GL10 unused, int width, int height) {
            GLES20.glViewport(0, 0, width, height);

            Matrix.perspectiveM(mMVPMatrix, 0, 45, (float) width / height, 0.1f, 100f);
            // z = -2.5 是全屏
            // z = -5 居中 1/4大小
            Matrix.translateM(mMVPMatrix, 0, 0f, 0f, -10f);
        }

        @Override
        //绘制每一帧的时候回调
        public void onDrawFrame(GL10 unused) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            GLES20.glUniformMatrix4fv(mMatrixHandle, 1, false, mMVPMatrix, 0);
            GLES20.glUniform1i(mTexSamplerHandle, 0);

            // 用 glDrawElements 来绘制，mVertexIndexBuffer 指定了顶点绘制顺序
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, VERTEX_INDEX.length,
                    GLES20.GL_UNSIGNED_SHORT, mVertexIndexBuffer);

        }

        void destroy() {
            GLES20.glDeleteTextures(1, new int[] { mTexName }, 0);
        }
    }
}
