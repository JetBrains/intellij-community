package org.jetbrains.completion.full.line.settings.ui.components

import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.JBColor
import com.intellij.util.ui.AsyncProcessIcon
import org.jetbrains.completion.full.line.decoratedMessage
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.runAsync
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

class LoadingComponent {
  val loadingIcon = AsyncProcessIcon("")
  val statusText = MultiLineLabel("")

  private val red = JBColor.RED
  private val green = JBColor(JBColor.GREEN.darker(), JBColor.GREEN.brighter())

  init {
    changeState(State.PROCESSED)
  }

  private fun changeState(state: State, @NlsContexts.StatusText msg: String? = null) {
    when (state) {
      State.PROCESSED -> setProcessed(msg)
      State.LOADING -> setLoading(msg)
      State.SUCCESS -> setSuccess(msg)
      State.ERROR -> setError(msg)
    }
  }

  private fun setLoading(@NlsContexts.StatusText msg: String?) {
    loadingIcon.resume()
    loadingIcon.isVisible = true
    statusText.isVisible = false
    statusText.text = msg
  }

  private fun setSuccess(@NlsContexts.StatusText msg: String?) {
    statusText.text = msg
    statusText.foreground = green
  }

  private fun setError(@NlsContexts.StatusText msg: String?) {
    statusText.text = msg
    statusText.foreground = red
  }

  private fun setProcessed(@NlsContexts.StatusText msg: String?) {
    if (msg != null) {
      statusText.text = msg
    }

    loadingIcon.suspend()
    loadingIcon.isVisible = false
    statusText.isVisible = statusText.text.isNotEmpty()
  }


  fun withAsyncProgress(promise: Promise<Pair<State, @NlsContexts.StatusText String>>, errorHandler: (Throwable) -> String? = { null }) {
    changeState(State.LOADING)
    runAsync {
      promise.blockingGet(60000)
    }.onSuccess {
      if (it != null) {
        changeState(it.first, it.second)
      }
    }.onError {
      val customHandler = errorHandler(it)
      if (customHandler != null) {
        changeState(State.ERROR, customHandler)
        return@onError
      }

      val res = when (it) {
        is SocketTimeoutException, is TimeoutException -> message("full.line.error.timeout")
        is ExecutionException -> it.decoratedMessage()
        else -> it.localizedMessage
      }
      changeState(State.ERROR, res)
    }.onProcessed {
      changeState(State.PROCESSED)
    }
  }

  enum class State {
    LOADING, SUCCESS, ERROR, PROCESSED
  }

}
