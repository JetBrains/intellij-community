package com.intellij.execution.process

import com.intellij.openapi.util.Key
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

open class CompositeProcessHandler(val handlers: List<ProcessHandler>) : ProcessHandler() {
  private val terminatedCount = AtomicInteger()

  private inner class EachListener : ProcessAdapter() {
    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
      this@CompositeProcessHandler.notifyTextAvailable(event.text, outputType)
    }

    override fun processTerminated(event: ProcessEvent) {
      val terminated = terminatedCount.incrementAndGet()
      if (terminated == handlers.size) {
        this@CompositeProcessHandler.notifyProcessTerminated(0)
      }
    }
  }

  init {
    assert(handlers.isNotEmpty())
    handlers.forEach { it.addProcessListener(EachListener()) }
  }

  override fun startNotify() {
    super.startNotify()
    handlers.forEach { startNotifyHandler(it) }
  }

  protected open fun startNotifyHandler(handler: ProcessHandler) {
    handler.startNotify()
  }

  override fun destroyProcessImpl() {
    handlers.forEach { it.destroyProcess() }
  }

  override fun detachProcessImpl() {
    handlers.forEach { it.detachProcess() }
  }

  override fun detachIsDefault(): Boolean = false
  override fun getProcessInput(): OutputStream? = null
}
