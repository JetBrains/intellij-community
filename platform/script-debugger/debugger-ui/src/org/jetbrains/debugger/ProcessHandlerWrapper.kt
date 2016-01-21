/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.debugger

import com.intellij.execution.KillableProcess
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.xdebugger.XDebugProcess
import org.jetbrains.rpc.LOG

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

  override fun isSilentlyDestroyOnClose() = handler.isSilentlyDestroyOnClose

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
      .done { stopProcess(destroy) }
      .rejected {
        try {
          LOG.error(it)
        }
        finally {
          stopProcess(destroy)
        }
      }
  }

  override fun detachIsDefault() = handler.detachIsDefault()

  override fun getProcessInput() = handler.processInput

  override fun canKillProcess() = handler is KillableProcess && handler.canKillProcess()

  override fun killProcess() {
    if (handler is KillableProcess) {
      handler.killProcess()
    }
  }
}