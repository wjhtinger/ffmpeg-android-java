package com.github.hiteshsondhi88.sampleffmpeg;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import javax.inject.Inject;

import butterknife.ButterKnife;
import butterknife.InjectView;
import dagger.ObjectGraph;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SocketDataShow extends Activity implements View.OnClickListener {

    private static final String TAG = SocketDataShow.class.getSimpleName();

    @Inject
    FFmpeg ffmpeg;

    @InjectView(R.id.command)
    EditText commandEditText;

    @InjectView(R.id.command_output)
    LinearLayout outputLayout;

    @InjectView(R.id.run_command)
    Button runButton;

    ImageView imageView;

    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_socket_data_show);
        ButterKnife.inject(this);
        //ObjectGraph.create(new DaggerDependencyModule(this)).inject(this);

        prepareWelt();
        loadFFMpegBinary();
        initUI();
        startServer();
    }

    private void prepareWelt()
    {   String innerPath = getFilesDir().getAbsolutePath();
        Log.e(TAG, "#########:" + innerPath);
        String meltFilePath = innerPath + "/mlt/bin/melt";
        File ffmpegFile = new File(meltFilePath);
        if(ffmpegFile.exists()){
            //return;
        }

        try {
            CopyAssetsUtil.copyAssets(this, "mlt", innerPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initUI() {
        commandEditText = (EditText)findViewById(R.id.command);
        outputLayout = (LinearLayout)findViewById(R.id.command_output);
        runButton = (Button)findViewById(R.id.run_command);
        imageView = (ImageView)findViewById(R.id.ImageView) ;

        runButton.setOnClickListener(this);

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(null);

        commandEditText.setText("avformat:/sdcard/123456789/NORM0128.MP4 -filter greyscale in=0 out=150 -consumer socket terminate_on_pause=1");
    }

    private void loadFFMpegBinary() {
        try {
            ffmpeg = FFmpeg.getInstance(getApplicationContext());
            ffmpeg.loadBinary(new LoadBinaryResponseHandler() {
                @Override
                public void onFailure() {
                    showUnsupportedExceptionDialog();
                }
            });
        } catch (FFmpegNotSupportedException e) {
            showUnsupportedExceptionDialog();
        }
    }

    private void execFFmpegBinary(final String[] command) {
        Map<String, String> envMaps = new HashMap<>();
        String innerPath = getFilesDir().getAbsolutePath();
        String envLd = innerPath + "/mlt/lib";
        String envMr = innerPath + "/mlt/lib/mlt";
        envMaps.put("LD_LIBRARY_PATH", envLd);
        envMaps.put("MLT_REPOSITORY", envMr);

        try {
            ffmpeg.execute(envMaps, command, new ExecuteBinaryResponseHandler() {
                @Override
                public void onFailure(String s) {
                    addTextViewToLayout("FAILED with output : "+s);
                }

                @Override
                public void onSuccess(String s) {
                    addTextViewToLayout("SUCCESS with output : "+s);
                }

                @Override
                public void onProgress(String s) {
                    Log.d(TAG, "Started command : ffmpeg "+command);
                    addTextViewToLayout("progress : "+s);
                    progressDialog.setMessage("Processing\n"+s);
                }

                @Override
                public void onStart() {
                    outputLayout.removeAllViews();

                    Log.d(TAG, "Started command : ffmpeg " + command);
                    progressDialog.setMessage("Processing...");
                    //progressDialog.show();
                }

                @Override
                public void onFinish() {
                    Log.d(TAG, "Finished command : ffmpeg "+command);
                    progressDialog.dismiss();
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            // do nothing for now
        }
    }

    private void addTextViewToLayout(String text) {
        TextView textView = new TextView(SocketDataShow.this);
        textView.setText(text);
        outputLayout.addView(textView);
    }

    private void showUnsupportedExceptionDialog() {
        new AlertDialog.Builder(SocketDataShow.this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(getString(R.string.device_not_supported))
                .setMessage(getString(R.string.device_not_supported_message))
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SocketDataShow.this.finish();
                    }
                })
                .create()
                .show();

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.run_command:
                String cmd = commandEditText.getText().toString();
                String[] command = cmd.split(" ");
                if (command.length != 0) {
                    execFFmpegBinary(command);
                } else {
                    Toast.makeText(SocketDataShow.this, getString(R.string.empty_command_toast), Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    private void startServer(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "startServer run!");
                LocalServerSocket server = null;
                byte[] data = new byte[1920*1080*4];
                int readSize, index, length_tmp;

                try {
                    server = new LocalServerSocket("mlt.socket");
                    LocalSocket receiver = server.accept();
                    InputStream input = receiver.getInputStream();
                    if(receiver != null){
                        while (true){
                            index = 0;
                            length_tmp = 1920*1080*4;
                            while ((readSize = input.read(data, (int) index, (int) length_tmp)) != -1) {
                                length_tmp -= readSize;
                                if (length_tmp == 0) {
                                    break;
                                }
                                index = index + readSize;
                            }

                            Log.e(TAG, "LocalServerSocket read data:" + length_tmp);

                            final Bitmap bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
                            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(data));

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    imageView.setImageBitmap(bitmap);
                                }
                            });
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }).start();

    }
}
