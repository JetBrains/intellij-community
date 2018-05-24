// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger

import com.intellij.execution.KillableProcess
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.xdebugger.XDebugProcess
import org.jetbrains.rpc.LOG
import java.io.OutputStream

class ProcessHandlerWrapper(private val debugProcess: XDebugProcess, private val handler: ProcessHandler) : ProcessHandler(), KillableProcess {
  init {
    if (handler.isStartNotified) {
      super.startNotify()
    }

    handler.addProcessListener(object : ProcessAdapter() {
      override fun startNotified(event: ProcessEvent) {
        super@ProcessHandlerWrapper.startNotify()
      }

      override fun processTerminated(event: ProcessEvent) {
        notifyProcessTerminated(event.exitCode)
      }
    })
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
      .onSuccess() { stopProcess(destroy) }
      .onError {
        try {
          LOG.error(it)
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