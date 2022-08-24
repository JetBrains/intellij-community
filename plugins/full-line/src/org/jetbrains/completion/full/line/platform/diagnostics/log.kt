package org.jetbrains.completion.full.line.platform.diagnostics

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.EventDispatcher
import java.util.*


private val LOG = logger<DiagnosticsService>()


interface FullLineLogger {
  fun error(throwable: Throwable)
  fun debug(text: String)
  fun debug(text: String, throwable: Throwable)
  fun info(text: String)
  fun warn(text: String, throwable: Throwable)

  val isDebugEnabled: Boolean

}

@Service
class DiagnosticsService {
  private val dispatcher = EventDispatcher.create(DiagnosticsListener::class.java)

  fun subscribe(listener: DiagnosticsListener, parentDisposable: Disposable) {
    dispatcher.addListener(listener, parentDisposable)
  }

  fun logger(part: FullLinePart, log: Logger = LOG): FullLineLogger {
    return object : FullLineLogger {
      override fun error(throwable: Throwable) = warn("Error happened", throwable)

      override fun debug(text: String) = onMessage(text) { debug(text) }

      override fun info(text: String) = onMessage(text) { info(text) }

      override fun debug(text: String, throwable: Throwable) = onMessage(text) { debug(text, throwable) }

      override fun warn(text: String, throwable: Throwable) = onMessage(text) { warn(text, throwable) }

      private fun onMessage(text: String, block: Logger.() -> Unit) {
        log.block()
        notifyListeners(text)
      }

      private fun notifyListeners(text: String) {
        if (!dispatcher.hasListeners()) return
        val message = DiagnosticsListener.Message(text, System.currentTimeMillis(), part)
        dispatcher.multicaster.messageReceived(message)
      }

      override val isDebugEnabled: Boolean = log.isDebugEnabled || dispatcher.hasListeners()
    }
  }

  companion object {
    fun getInstance(): DiagnosticsService {
      return service()
    }
  }
}

inline fun <reified T : Any> logger(part: FullLinePart): FullLineLogger = DiagnosticsService.getInstance().logger(part, logger<T>())

interface DiagnosticsListener : EventListener {
  fun messageReceived(message: Message)

  data class Message(val text: String, val time: Long, val part: FullLinePart)
}

enum class FullLinePart {
  BEAM_SEARCH, // beam search of local models
  NETWORK, // answers and delays for network
  PRE_PROCESSING, // preprocessing of FL proposals before showing
  POST_PROCESSING, // preprocessing of FL proposals after showing
}
