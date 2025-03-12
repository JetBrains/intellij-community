// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.execution.KillableProcess
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.xdebugger.impl.rhizome.XDebugSessionEntity
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XDebugSessionProcessHandlerApi
import com.jetbrains.rhizomedb.entity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

internal class BackendXDebugSessionProcessHandlerApi : XDebugSessionProcessHandlerApi {
  override suspend fun startNotify(sessionId: XDebugSessionId) {
    val session = entity(XDebugSessionEntity.SessionId, sessionId)?.session ?: return
    session.debugProcess.processHandler.startNotify()
  }

  override suspend fun waitFor(sessionId: XDebugSessionId, timeoutInMilliseconds: Long?): Deferred<Boolean> {
    val session = entity(XDebugSessionEntity.SessionId, sessionId)?.session ?: return CompletableDeferred(true)
    val processHandler = session.debugProcess.processHandler
    return session.project.service<BackendXDebugSessionProcessHandlerApiCoroutineScope>().cs.async(Dispatchers.IO) {
      if (timeoutInMilliseconds != null) {
        processHandler.waitFor(timeoutInMilliseconds)
      }
      else {
        processHandler.waitFor()
      }
    }
  }

  override suspend fun destroyProcess(sessionId: XDebugSessionId) {
    val session = entity(XDebugSessionEntity.SessionId, sessionId)?.session ?: return
    session.debugProcess.processHandler.destroyProcess()
  }

  override suspend fun detachProcess(sessionId: XDebugSessionId) {
    val session = entity(XDebugSessionEntity.SessionId, sessionId)?.session ?: return
    session.debugProcess.processHandler.detachProcess()
  }

  override suspend fun killProcess(sessionId: XDebugSessionId) {
    val session = entity(XDebugSessionEntity.SessionId, sessionId)?.session ?: return
    val processHandler = session.debugProcess.processHandler
    if (processHandler is KillableProcess) {
      processHandler.killProcess()
    }
  }
}


@Service(Service.Level.PROJECT)
private class BackendXDebugSessionProcessHandlerApiCoroutineScope(val cs: CoroutineScope)