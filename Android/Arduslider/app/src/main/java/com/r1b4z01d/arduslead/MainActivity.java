package com.r1b4z01d.arduslead;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // GUI Components
    private TextView mBluetoothStatus;
    private EditText ShutterSpeed;
    private EditText msOfCapture;
    private EditText bufferClear;
    private EditText mmOfTravel;
    private TextView photo;
    private TextView photoCount;
    private TextView VideoSeconds;
    private TextView MMPerPhoto;
    private ProgressBar photoProgressBar;
    private Button mOnBtn;
    private Button mOffBtn;
    private LinearLayout UserControls;
    private Button mListPairedDevicesBtn;
    private BluetoothAdapter mBTAdapter;
    private Set<BluetoothDevice> mPairedDevices;
    private ArrayAdapter<String> mBTArrayAdapter;
    private ListView mDevicesListView;
    private CheckBox mLED1;
    private Handler mHandler; // Our main handler that will receive callback notifications
    private ConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data
    private BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path

    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier


    // #defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status



    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBluetoothStatus = findViewById(R.id.bluetoothStatus);
        mOnBtn = findViewById(R.id.on);
        mOffBtn = findViewById(R.id.off);
        mListPairedDevicesBtn = findViewById(R.id.PairedBtn);
        mLED1 = findViewById(R.id.checkboxLED1);
        ShutterSpeed = findViewById(R.id.shutterSpeed);
        msOfCapture = findViewById(R.id.msOfCapture);
        bufferClear = findViewById(R.id.bufferClear);
        mmOfTravel = findViewById(R.id.mmOfTravel);
        MMPerPhoto = findViewById(R.id.MMPerPhoto);
        VideoSeconds = findViewById(R.id.VideoSeconds);
        photo = findViewById(R.id.photo);
        photoCount = findViewById(R.id.photoCount);
        photoProgressBar = findViewById(R.id.photoProgressBar);
        UserControls = findViewById(R.id.UserControls);
        UserControls.setVisibility(View.GONE);

        mBTArrayAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1);
        mBTAdapter = BluetoothAdapter.getDefaultAdapter(); // get a handle on the bluetooth radio

        mDevicesListView = (ListView)findViewById(R.id.devicesListView);
        mDevicesListView.setAdapter(mBTArrayAdapter); // assign model to view
        mDevicesListView.setOnItemClickListener(mDeviceClickListener);

        if(mBTAdapter.isEnabled()){
            mOnBtn.setVisibility(View.GONE);
            mOffBtn.setVisibility(View.VISIBLE);
            mBluetoothStatus.setText("Bluetooth Enabled");
        }else{
            mOffBtn.setVisibility(View.GONE);
            mOnBtn.setVisibility(View.VISIBLE);
            mBluetoothStatus.setText("Bluetooth Disabled");
        }

        mHandler = new Handler(){
            public void handleMessage(android.os.Message msg){
                if(msg.what == MESSAGE_READ){
                    String readMessage = null;
                    try {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                        String [] dataSlices = null;
                        if(readMessage.length()>=2){
                            dataSlices = readMessage.split("\n", -2);
                            for(String dataSlice:dataSlices) {
                                String dataFlag = Character.toString(dataSlice.charAt(0)); //takes first letter
                                String data = dataSlice.substring(1); //takes rest of sentence

                                if (dataFlag.equals("V"))
                                    VideoSeconds.setText(data.toString().trim());
                                if (dataFlag.equals("M"))
                                    MMPerPhoto.setText(data.toString().trim());
                                if (dataFlag.equals("S"))
                                    ShutterSpeed.setText(data.toString().trim());
                                if (dataFlag.equals("T"))
                                    msOfCapture.setText(data.toString().trim());
                                if (dataFlag.equals("B"))
                                    bufferClear.setText(data.toString().trim());
                                if (dataFlag.equals("D"))
                                    mmOfTravel.setText(data.toString().trim());
                                if (dataFlag.equals("P")) {
                                    photo.setText(data.toString().trim());
                                    photoProgressBar.setProgress(Integer.parseInt(data.toString().trim()));
                                }
                                if (dataFlag.equals("C")) {
                                    photoCount.setText(data.toString().trim());

                                    photoProgressBar.setMax(Integer.parseInt(data.toString().trim().substring(0, data.toString().trim().length() - 3)));
                                }
                                if (dataFlag.equals("R")) {
                                    if (Integer.parseInt(data.toString().trim()) == 0) {
                                        mLED1.setChecked(false);
                                    } else {
                                        mLED1.setChecked(true);
                                    }
                                }
                            }
                        }

                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }

                }

                if(msg.what == CONNECTING_STATUS){
                    if(msg.arg1 == 1){
                        mBluetoothStatus.setText("Connected to Device: " + (String)(msg.obj));
                        mConnectedThread.write("A");
                    }
                    else
                        mBluetoothStatus.setText("Connection Failed");
                }
            }
        };

        if (mBTArrayAdapter == null) {
            // Device does not support Bluetooth
            mBluetoothStatus.setText("Status: Bluetooth not found");
            Toast.makeText(getApplicationContext(),"Bluetooth device not found!",Toast.LENGTH_SHORT).show();
        }
        else {
            mLED1.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    if(mConnectedThread != null) //First check to make sure thread created
                       if(mLED1.isChecked()){
                           mConnectedThread.write("R1");
                       }else{
                           mConnectedThread.write("R0");
                       }
                }
            });

            mmOfTravel.setOnFocusChangeListener(new View.OnFocusChangeListener(){
                @Override
                public void onFocusChange(View arg0, boolean hasFocus) {
                    if(!hasFocus){
                        mConnectedThread.write("D"+mmOfTravel.getText());
                    }
                }
            });

            bufferClear.setOnFocusChangeListener(new View.OnFocusChangeListener(){
                @Override
                public void onFocusChange(View arg0, boolean hasFocus) {
                    if(!hasFocus){
                        mConnectedThread.write("B"+bufferClear.getText());

                    }
                }
            });
            ShutterSpeed.setOnFocusChangeListener(new View.OnFocusChangeListener(){
                @Override
                public void onFocusChange(View arg0, boolean hasFocus) {
                    if(!hasFocus){
                        mConnectedThread.write("S"+ShutterSpeed.getText());
                    }
                }
            });
            msOfCapture.setOnFocusChangeListener(new View.OnFocusChangeListener(){
                @Override
                public void onFocusChange(View arg0, boolean hasFocus) {
                    if(!hasFocus){
                        mConnectedThread.write("T"+msOfCapture.getText());
                    }
                }
            });

            mOnBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    bluetoothOn(v);
                }
            });

            mOffBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    bluetoothOff(v);
                }
            });

            mListPairedDevicesBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v){
                    listPairedDevices(v);
                }
            });

        }
    }

    private void bluetoothOn(View view){
        if (!mBTAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            mBluetoothStatus.setText("Bluetooth enabled");
            Toast.makeText(getApplicationContext(),"Bluetooth turned on",Toast.LENGTH_SHORT).show();
            mOnBtn.setVisibility(View.GONE);
            mOffBtn.setVisibility(View.VISIBLE);
        }
        else{
            Toast.makeText(getApplicationContext(),"Bluetooth is already on", Toast.LENGTH_SHORT).show();
        }
    }

    // Enter here after user selects "yes" or "no" to enabling radio
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data){
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                mBluetoothStatus.setText("Bluetooth Enabled");
            }
            else
                mBluetoothStatus.setText("Bluetooth Disabled");
        }
    }

    private void bluetoothOff(View view){
        mBTAdapter.disable(); // turn off
        mBluetoothStatus.setText("Bluetooth Disabled");
        mOffBtn.setVisibility(View.GONE);
        mOnBtn.setVisibility(View.VISIBLE);
        Toast.makeText(getApplicationContext(),"Bluetooth turned Off", Toast.LENGTH_SHORT).show();
    }


    final BroadcastReceiver blReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // add the name to the list
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                mBTArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    private void listPairedDevices(View view){
        mPairedDevices = mBTAdapter.getBondedDevices();
        if(mBTAdapter.isEnabled()) {
            // put it's one to the adapter
            mBTArrayAdapter.clear();
            mDevicesListView.setVisibility(View.VISIBLE);
            for (BluetoothDevice device : mPairedDevices)
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            Toast.makeText(getApplicationContext(), "Show Paired Devices", Toast.LENGTH_SHORT).show();
        }
        else
            Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
    }

    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {

            if(!mBTAdapter.isEnabled()) {
                Toast.makeText(getBaseContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
                return;
            }
            mDevicesListView.setVisibility(View.GONE);
            mBluetoothStatus.setText("Connecting...");
            mOffBtn.setVisibility(View.GONE);
            mListPairedDevicesBtn.setVisibility(View.GONE);
            UserControls.setVisibility(View.VISIBLE);

            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            final String address = info.substring(info.length() - 17);
            final String name = info.substring(0,info.length() - 17);

            // Spawn a new thread to avoid blocking the GUI one
            new Thread()
            {
                public void run() {
                    boolean fail = false;

                    BluetoothDevice device = mBTAdapter.getRemoteDevice(address);

                    try {
                        mBTSocket = createBluetoothSocket(device);
                    } catch (IOException e) {
                        fail = true;
                        Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                    }
                    // Establish the Bluetooth socket connection.
                    try {
                        mBTSocket.connect();
                    } catch (IOException e) {
                        try {
                            fail = true;
                            mBTSocket.close();
                            mHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                    .sendToTarget();
                        } catch (IOException e2) {
                            //insert code to deal with this
                            Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                    if(fail == false) {
                        mConnectedThread = new ConnectedThread(mBTSocket);
                        mConnectedThread.start();
                        mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                                .sendToTarget();

                    }
                }
            }.start();

          
        }
    };

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
        //creates secure outgoing connection with BT device using UUID
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.available();
                    if(bytes != 0) {
                        buffer =  new byte[1024];
                        SystemClock.sleep(200); //pause and wait for rest of data. Adjust this depending on your sending speed.
                        bytes = mmInStream.available(); // how many bytes are ready to be read?
                        bytes = mmInStream.read(buffer, 0, bytes); // record how many bytes we actually read
                        mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                                .sendToTarget(); // Send the obtained bytes to the UI activity
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String input) {
            byte[] bytes = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
}

