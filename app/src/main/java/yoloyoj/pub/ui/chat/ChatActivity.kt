package yoloyoj.pub.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.storage.FirebaseStorage
import kotlinx.android.synthetic.main.activity_chat.*
import yoloyoj.pub.MainActivity
import yoloyoj.pub.R
import yoloyoj.pub.models.Attachment
import yoloyoj.pub.ui.attachment.preview.AttachmentPreviewAdapter
import yoloyoj.pub.web.apiClient
import yoloyoj.pub.web.handlers.MessageSender
import java.io.File


const val MY_USER_ID = 1

const val EXTRA_CHATID = "chatid"

const val CODE_GET_PICTURE = 1

class ChatActivity : AppCompatActivity() {

    private lateinit var viewModel: ChatViewModel
    private lateinit var messages: MessagesData
    private lateinit var attachments: MutableLiveData<MutableList<Attachment>>

    private lateinit var messageSender: MessageSender

    private var chatid: Int? = null

    private var userid: Int = 0

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (data != null)
        when (requestCode) {
            CODE_GET_PICTURE -> putImage(data.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        chatid = intent.getIntExtra(EXTRA_CHATID, 0)

        userid = getSharedPreferences(MainActivity.PREFERENCES_USER, Context.MODE_PRIVATE)
            .getInt(MainActivity.PREFERENCES_USERID, 0)

        viewModel = ViewModelProviders.of(this).get(ChatViewModel::class.java)
        viewModel.chatid = chatid

        viewModel.messages.value = mutableListOf()
        messages = viewModel.messages
        attachments = viewModel.attachments
    }

    override fun onStart() {
        viewModel.messageGetter.start( chatid!!, 0)

        messageSender = MessageSender(sendButton)

        messages.observeForever { loadAdapter() }

        messagesView.apply {
            layoutManager = LinearLayoutManager(context)
        }

        loadAttachmentPreview()

        loadWriteWatcher()

        loadOnClicks()

        super.onStart()
    }

    override fun onResume() {
        messages.value = emptyList()

        super.onResume()
    }

    private fun loadAttachmentPreview() {
        attachmentsPreView.adapter = AttachmentPreviewAdapter(
            attachmentsPreView,
            attachments
        )

        attachments.observeForever {
            (attachmentsPreView.adapter as AttachmentPreviewAdapter).apply {
                items = attachments
                notifyDataSetChanged()

                if (items.value!!.isEmpty()) {
                    attachmentsPreView.visibility = View.GONE
                    addAttachment.drawable.setTint(resources.getColor(R.color.colorAccent))
                } else if (attachmentsPreView.visibility == View.GONE) {
                    attachmentsPreView.visibility = View.VISIBLE
                    addAttachment.drawable.setTint(resources.getColor(R.color.colorAccent))
                }
            }
        }
    }

    private fun loadWriteWatcher() {
        editMessage.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s!!.isBlank())
                    sendButton.visibility = View.GONE
                else
                    sendButton.visibility = View.VISIBLE
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        })
    }

    private fun loadAdapter() {
        try {
            messagesView?.adapter = ChatAdapter(
                messages.value!!
            )
            messagesView.scrollToPosition(
                messagesView.adapter!!.itemCount - 1
            )
        } catch (e: Exception) {
        }
    }

    private fun loadOnClicks() {
        sendButton.setOnClickListener { sendMessage() }
    }

    private fun sendMessage() {
        apiClient.putMessage(
            editMessage.text.toString(),
            userid,
            chatid!!,
            attachments.value!!.map{ it.attachment_link }.joinToString(";")
        )?.enqueue(messageSender)
        editMessage.text.clear()

        onImageSent()
    }

    @Suppress("UNUSED_PARAMETER")
    fun addAttachment(view: View) {
        val intent = Intent().apply {
            type = "image/*"
            action = Intent.ACTION_GET_CONTENT
        }

        startActivityForResult(
            Intent.createChooser(intent, "Select picture"),
            CODE_GET_PICTURE
        )
    }

    private fun putImage(uri: Uri) {
        val file = File(uri.path)

        val storage = FirebaseStorage.getInstance()
        val storageReference = storage
            .getReferenceFromUrl("gs://vpabe-75c05.appspot.com") // TODO: remove hardcode
            .child("${file.hashCode()}.${uri.path!!.split(".").last()}")

        storageReference.putFile(uri)
        storageReference.downloadUrl.addOnSuccessListener {
            onImagePutted(it.path!!)
        }
    }

    private fun onImagePutted(link: String) {
        attachments.value = (attachments.value!! + Attachment("image", "https://firebasestorage.googleapis.com$link?alt=media")).toMutableList()

        (attachmentsPreView.adapter as AttachmentPreviewAdapter).apply {
            items = attachments // temporary

            if (recyclerView.visibility == View.GONE)
                recyclerView.visibility = View.VISIBLE
        }
    }

    private fun onImageSent() {
        addAttachment.drawable.setTint(resources.getColor(R.color.colorAccent))

        attachments.value = mutableListOf()
    }
}
