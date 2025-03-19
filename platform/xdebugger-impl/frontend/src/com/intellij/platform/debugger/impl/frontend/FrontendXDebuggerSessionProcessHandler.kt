// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.execution.KillableProcess
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.xdebugger.impl.rpc.KillableProcessInfo
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XDebugSessionProcessHandlerApi
import com.intellij.xdebugger.impl.rpc.XDebuggerProcessHandlerDto
import com.intellij.xdebugger.impl.rpc.XDebuggerProcessHandlerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.OutputStream

internal fun createProcessHandler(
  project: Project,
  sessionId: XDebugSessionId,
  processHandlerDto: XDebuggerProcessHandlerDto,
): ProcessHandler {
  val killableProcessInfo = processHandlerDto.killableProcessInfo
  return if (killableProcessInfo != null) {
    FrontendXDebuggerSessionKillableProcessHandler(project, sessionId, processHandlerDto, killableProcessInfo)
  }
  else {
    FrontendXDebuggerSessionProcessHandler(project, sessionId, processHandlerDto)
  }
}

// TODO: check for possible races because of cs.launch in all class methods
private open class FrontendXDebuggerSessionProcessHandler(
  project: Project,
  protected val sessionId: XDebugSessionId,
  protected val processHandlerDto: XDebuggerProcessHandlerDto,
) : ProcessHandler() {
  // TODO: use better CoroutineScope
  protected val cs: CoroutineScope = project.service<FrontendXDebuggerSessionProcessHandlerCoroutineScope>().cs

  init {
    cs.launch {
      processHandlerDto.processHandlerEvents.toFlow().collect { event ->
        when (event) {
          is XDebuggerProcessHandlerEvent.StartNotified -> {
            if (!isStartNotified) {
              startNotify()
            }
          }
          is XDebuggerProcessHandlerEvent.ProcessTerminated -> {
            destroyProcess()
          }
          is XDebuggerProcessHandlerEvent.ProcessWillTerminate -> {
            destroyProcess()
          }
          is XDebuggerProcessHandlerEvent.OnTextAvailable -> {
            // TODO: DONT create Key every time
            notifyTextAvailable(event.eventData.text ?: "", Key.create<Any?>(event.key))
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
      val exitCode = XDebugSessionProcessHandlerApi.getInstance().destroyProcess(sessionId).await()
      notifyProcessTerminated(exitCode ?: 0)
    }
  }

  override fun detachProcessImpl() {
    cs.launch {
      XDebugSessionProcessHandlerApi.getInstance().detachProcess(sessionId).await()
      notifyProcessDetached()
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

private class FrontendXDebuggerSessionKillableProcessHandler(
  project: Project,
  sessionId: XDebugSessionId,
  processHandlerDto: XDebuggerProcessHandlerDto,
  private val killableProcessInfo: KillableProcessInfo,
) : FrontendXDebuggerSessionProcessHandler(project, sessionId, processHandlerDto), KillableProcess {

  override fun canKillProcess(): Boolean {
    return killableProcessInfo.canKillProcess
  }

  override fun killProcess() {
    if (canKillProcess()) {
      cs.launch {
        XDebugSessionProcessHandlerApi.getInstance().killProcess(sessionId)
      }
    }
  }
}

private val LOG = fileLogger()

@Service(Service.Level.PROJECT)
private class FrontendXDebuggerSessionProcessHandlerCoroutineScope(val cs: CoroutineScope)