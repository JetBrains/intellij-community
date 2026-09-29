package com.intellij.execution.process

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

/**
 * Captures output until the process stops or the capture is disposed.
 */
@ApiStatus.Internal
class ProcessOutputCapture(
  handler: ProcessHandler,
  private val output: CapturedProcessOutput,
  private val onStopped: () -> Unit = {},
) : ProcessListener, Disposable {
  private var handler: ProcessHandler? = handler
  private var started = false

  fun start() {
    val handler = synchronized(this) {
      if (started) return
      val handler = this.handler ?: return
      started = true
      handler
    }
    handler.addProcessListener(this)

    val alreadyStoppedOrDisposed = synchronized(this) {
      this.handler == null
    }
    if (alreadyStoppedOrDisposed) {
      handler.removeProcessListener(this)
    }
    handler.exitCode?.let(::stop)
  }

  @Synchronized
  override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
    if (handler != null && !ProcessOutputType.isSystem(outputType)) {
      output.append(event.text)
    }
  }

  override fun processTerminated(event: ProcessEvent) {
    stop(event.exitCode)
  }

  override fun processNotStarted() {
    stop(null)
  }

  private fun stop(code: Int?) {
    val handler = synchronized(this) {
      val handler = this.handler ?: return
      this.handler = null
      output.markStopped(code)
      handler
    }
    handler.removeProcessListener(this)
    onStopped()
  }

  override fun dispose() {
    val handler = synchronized(this) {
      this.handler.also { this.handler = null }
    }
    handler?.removeProcessListener(this)
  }
}
