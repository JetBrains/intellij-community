// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger

import com.intellij.execution.KillableProcess
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.xdebugger.XDebugProcess
import java.io.OutputStream

@Deprecated("Please consider implementing own wrapper")
class ProcessHandlerWrapper(private val debugProcess: XDebugProcess, private val handler: ProcessHandler) : ProcessHandler(), KillableProcess {
  init {
    if (handler.isStartNotified) {
      super.startNotify()
    }

    handler.addProcessListener(object : ProcessListener {
      override fun startNotified(event: ProcessEvent) {
        super@ProcessHandlerWrapper.startNotify()
      }

      override fun processTerminated(event: ProcessEvent) {
        notifyProcessTerminated(event.exitCode)
      }
    })
  }

  override fun addProcessListener(listener: ProcessListener) {
    handler.addProcessListener(listener)
  }

  override fun addProcessListener(listener: ProcessListener, parentDisposable: Disposable) {
    handler.addProcessListener(listener, parentDisposable)
  }

  override fun removeProcessListener(listener: ProcessListener) {
    handler.removeProcessListener(listener)
  }

  override fun isSilentlyDestroyOnClose(): Boolean = handler.isSilentlyDestroyOnClose

  override fun startNotify() {
    handler.startNotify()
  }

  override fun destroyProcessImpl() {
    stop(true)
  }

  override fun detachProcessImpl() {
    stop(false)
  }

  private fun stop(destroy: Boolean) {
    fun stopProcess(destroy: Boolean) {
      if (destroy) {
        handler.destroyProcess()
      }
      else {
        handler.detachProcess()
      }
    }

    debugProcess.stopAsync()
      .onSuccess { stopProcess(destroy) }
      .onError {
        try {
          thisLogger().error(it)
        }
        finally {
          stopProcess(destroy)
        }
      }
  }

  override fun detachIsDefault(): Boolean = handler.detachIsDefault()

  override fun getProcessInput(): OutputStream? = handler.processInput

  override fun canKillProcess(): Boolean = handler is KillableProcess && handler.canKillProcess()

  override fun killProcess() {
    if (handler is KillableProcess) {
      handler.killProcess()
    }
  }
}