// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XDebugSessionProcessHandlerApi
import com.intellij.xdebugger.impl.rpc.XDebuggerProcessHandlerDto
import com.intellij.xdebugger.impl.rpc.XDebuggerProcessHandlerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.OutputStream

// TODO: check for possible races because of cs.launch in all class methods
internal class FrontendXDebuggerSessionProcessHandler(
  private val project: Project,
  private val sessionId: XDebugSessionId,
  private val processHandlerDto: XDebuggerProcessHandlerDto,
) : ProcessHandler() {
  // TODO: use better CoroutineScope
  private val cs: CoroutineScope = project.service<FrontendXDebuggerSessionProcessHandlerCoroutineScope>().cs

  init {
    cs.launch {
      processHandlerDto.processHandlerEvents.toFlow().collect { event ->
        when (event) {
          is XDebuggerProcessHandlerEvent.StartNotified -> {
            startNotify()
          }
          is XDebuggerProcessHandlerEvent.ProcessTerminated -> {
            destroyProcess()
          }
          is XDebuggerProcessHandlerEvent.ProcessWillTerminate -> {
            destroyProcess()
          }
          is XDebuggerProcessHandlerEvent.OnTextAvailable -> {
            // TODO: DONT create Key every time
            notifyTextAvailable(event.eventData.text, Key.create<Any?>(event.key))
          }
          XDebuggerProcessHandlerEvent.ProcessNotStarted -> {
            // TODO: handle this case
          }
        }
      }
    }
  }

  override fun waitFor(): Boolean {
    return runBlockingMaybeCancellable {
      XDebugSessionProcessHandlerApi.getInstance().waitFor(sessionId, null).await()
    }
  }

  override fun waitFor(timeoutInMilliseconds: Long): Boolean {
    return runBlockingMaybeCancellable {
      XDebugSessionProcessHandlerApi.getInstance().waitFor(sessionId, timeoutInMilliseconds).await()
    }
  }

  override fun destroyProcessImpl() {
    cs.launch {
      XDebugSessionProcessHandlerApi.getInstance().destroyProcess(sessionId)
    }
  }

  override fun detachProcessImpl() {
    cs.launch {
      XDebugSessionProcessHandlerApi.getInstance().destroyProcess(sessionId)
    }
  }

  override fun detachIsDefault(): Boolean {
    return processHandlerDto.detachIsDefault
  }

  override fun getProcessInput(): OutputStream? {
    LOG.error("getProcessInput shouldn't be used on the frontend")
    return null
  }
}

private val LOG = fileLogger()

@Service(Service.Level.PROJECT)
private class FrontendXDebuggerSessionProcessHandlerCoroutineScope(val cs: CoroutineScope)