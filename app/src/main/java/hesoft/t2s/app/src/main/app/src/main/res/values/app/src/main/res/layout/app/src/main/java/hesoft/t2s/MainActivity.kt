package hesoft.t2s

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var isInitialized = false
    private var currentParagraph = 0
    private var paragraphs = listOf<String>()
    private var isPaused = false

    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                tts.stop()
                isPaused = true
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isPaused) playCurrentParagraph() // повторить абзац
            }
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent?.action) {
                tts.stop()
                isPaused = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        val textInput = findViewById<EditText>(R.id.text_input)
        val btnPlay = findViewById<Button>(R.id.btn_play)
        val btnPause = findViewById<Button>(R.id.btn_pause)
        val btnStop = findViewById<Button>(R.id.btn_stop)

        tts = TextToSpeech(this, this)

        btnPlay.setOnClickListener { playCurrentParagraph() }
        btnPause.setOnClickListener { pause() }
        btnStop.setOnClickListener { stop() }

        // Пример текста — потом сделаем загрузку
        updateParagraphs(textInput.text.toString())
        textInput.setOnTextChangedListener { updateParagraphs(it.toString()) }
    }

    private fun updateParagraphs(text: String) {
        paragraphs = text.split("\n\n").filter { it.isNotBlank() }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("ru", "RU")
            isInitialized = true
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onDone(utteranceId: String?) {
                    currentParagraph++
                    if (currentParagraph < paragraphs.size) playCurrentParagraph()
                }
                override fun onError(utteranceId: String?) {}
                override fun onStart(utteranceId: String?) {}
            })
        }
    }

    private fun playCurrentParagraph() {
        if (!isInitialized || paragraphs.isEmpty()) return
        if (requestAudioFocus()) {
            val text = paragraphs[currentParagraph]
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "para_$currentParagraph")
        }
    }

    private fun pause() {
        tts.stop()
        isPaused = true
    }

    private fun stop() {
        tts.stop()
        isPaused = false
        currentParagraph = 0
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attr)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            return audioManager.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        return true // fallback
    }

    override fun onDestroy() {
        tts.shutdown()
        unregisterReceiver(noisyReceiver)
        super.onDestroy()
    }
}
