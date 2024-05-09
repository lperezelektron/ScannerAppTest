package com.example.scannerapp

import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.scannerapp.databinding.ActivityMainBinding
import com.google.android.material.textfield.TextInputEditText
import com.keyence.autoid.sdk.SdkStatus
import com.keyence.autoid.sdk.deviceinfo.DeviceInfo
import com.keyence.autoid.sdk.deviceinfo.LicenceInfo
import com.keyence.autoid.sdk.notification.Notification
import com.keyence.autoid.sdk.scan.DecodeResult
import com.keyence.autoid.sdk.scan.ScanManager
import com.keyence.autoid.sdk.scan.scanparams.CodeType
import com.keyence.autoid.sdk.scan.scanparams.DataOutput
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), ScanManager.DataListener {

    private lateinit var _scanManager: ScanManager
    private lateinit var mNotification: Notification
    private lateinit var mDeviceInfo: DeviceInfo

    private lateinit var binding: ActivityMainBinding

    private val LOGFILE_NAME = "inventory.csv"
    private val dataOutput = DataOutput()
    private var _defaultKeyStrokeEnabled = true
    private val _defaultCodeType = CodeType()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initComponents()
        initListeners()
    }

    override fun onResume() {
        super.onResume()
        _scanManager.unlockScanner()
        configScanner()
        _scanManager.addDataListener(this)
        //showToast("Entramos a OnResume()")
    }

    override fun onPause() {
        super.onPause()
        _scanManager.unlockScanner()
        _scanManager.setConfig(_defaultCodeType)
        dataOutput.keyStrokeOutput.enabled = _defaultKeyStrokeEnabled
        _scanManager.setConfig(dataOutput)
        _scanManager.removeDataListener(this)
        //showToast("Entramos a OnPause()")
    }

    override fun onDestroy() {
        super.onDestroy()
        _scanManager.releaseScanManager()
        mNotification.releaseNotification()
        mDeviceInfo.releaseDeviceInfo();
        //showToast("Entramos a OnDestroy()")
    }

    private fun configScanner() {
        showToast(dataOutput.toString())
        _scanManager.getConfig(dataOutput)
        _defaultKeyStrokeEnabled = dataOutput.keyStrokeOutput.enabled

        dataOutput.keyStrokeOutput.enabled = false
        _scanManager.setConfig(dataOutput)
    }

    private fun initComponents() {
        _scanManager = ScanManager.createScanManager(this)
        mNotification = Notification.createNotification(this)
        mDeviceInfo = DeviceInfo.createDeviceInfo(this)
        _defaultCodeType.setDefault()
    }

    private fun initListeners() {
        binding.qtyInput.setOnKeyListener { v, keyCode, event ->
            if (event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                if (v is TextInputEditText) {
                    if (v.text?.isNotEmpty() == true) {
                        finishInput()
                        binding.operatorInput.requestFocus()
                        startBuzzer()
                    }
                }
            }
            false
        }

        binding.itemInput.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                _scanManager.getConfig(_defaultCodeType)
                val codeType = CodeType().apply {
                    setAllDisable()
                    upcEanJan = true
                }
                _scanManager.setConfig(codeType)
            } else {
                _scanManager.setConfig(_defaultCodeType)
            }
        }

        binding.qtyInput.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                _scanManager.lockScanner()
            } else {
                _scanManager.unlockScanner()
            }
        }

    }

    override fun onDataReceived(decodeResult: DecodeResult) {
        val view = currentFocus
        val elementName = view?.resources?.getResourceEntryName(view.id)
        val result = decodeResult.result
        val deviceModel = mDeviceInfo.bluetoothMACAddress
        showToast(deviceModel)
        if (view is TextInputEditText && decodeResult.result == DecodeResult.Result.SUCCESS) {
            val next = view.focusSearch(View.FOCUS_DOWN)
            if (next != null) {
                startLed(elementName.toString())
                view.setText(decodeResult.data)
                next.requestFocus()
            }
        }
    }

    private fun finishInput() {
        val data = StringBuilder(getTimeStamp())
        data.append(",").append(binding.operatorInput.text)
        data.append(",").append(binding.locationInput.text)
        data.append(",").append(binding.itemInput.text)
        data.append(",").append(binding.qtyInput.text)
        writeToLogFile(data.toString() + "\n")

        binding.locationInput.setText("")
        binding.itemInput.setText("")
        binding.qtyInput.setText("")
        binding.operatorInput.setText("")
    }

    private fun writeToLogFile(data: String) {
        val file = File(getExternalFilesDir(null), LOGFILE_NAME)
        try {
            val bufferedWriter = BufferedWriter(
                OutputStreamWriter(
                    /* out = */ FileOutputStream(file, true),
                    /* cs = */ StandardCharsets.UTF_8
                )
            )
            bufferedWriter.write(data)
            bufferedWriter.flush()
            bufferedWriter.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getTimeStamp(): String {
        val date = Date(System.currentTimeMillis())
        return SimpleDateFormat("yyyy/MM/dd,HH:mm:ss", Locale.US).format(date)
    }

    private fun startBuzzer(): Boolean {
        mNotification.startBuzzer(12, 200, 50, 2)
        return true
    }

    private fun stopBuzzer(): Boolean {
        mNotification.stopBuzzer()
        return true
    }

    private fun startLed(campo: String): Boolean {
        val color = when (campo) {
            "operatorInput" -> Notification.Led.BLUE
            "locationInput" -> Notification.Led.MAGENTA
            "itemInput" -> Notification.Led.GREEN
            else -> Notification.Led.CYAN
        }
        mNotification.startLed(color, 100, 50, 3)
        return true
    }

    private fun stopLed(): Boolean {
        mNotification.stopLed()
        return true
    }

    private fun showToast(text: String) {
        val duration = Toast.LENGTH_LONG
        val toast = Toast.makeText(this, text, duration)
        toast.show()
    }

}