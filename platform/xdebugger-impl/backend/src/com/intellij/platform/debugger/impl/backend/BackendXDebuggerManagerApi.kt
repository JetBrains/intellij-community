// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.rpc.XDebugSessionDto
import com.intellij.xdebugger.impl.rpc.XDebuggerManagerApi
import com.intellij.xdebugger.impl.rpc.XDebuggerSessionEvent
import com.intellij.xdebugger.impl.rpc.XDebuggerSessionInfoDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

internal class BackendXDebuggerManagerApi : XDebuggerManagerApi {
  @OptIn(ExperimentalCoroutinesApi::class)
  override suspend fun currentSession(projectId: ProjectId): Flow<XDebugSessionDto?> {
    val project = projectId.findProject()

    return (XDebuggerManager.getInstance(project) as XDebuggerManagerImpl).currentSessionFlow.mapLatest { currentSession ->
      if (currentSession == null) {
        return@mapLatest null
      }
      val entity = currentSession.entity.await()
      XDebugSessionDto(entity.sessionId, currentSession.debugProcess.editorsProvider)
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override suspend fun sessionEvents(projectId: ProjectId): Flow<XDebuggerSessionEvent> {
    val project = projectId.findProject()
    val dispatcher = Dispatchers.Default.limitedParallelism(1)
    return channelFlow {
      project.messageBus.connect(this).subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
        override fun processStarted(debugProcess: XDebugProcess) {
          val session = debugProcess.session as? XDebugSessionImpl ?: return
          launch(dispatcher) {
            val sessionId = session.entity.await().sessionId
            send(XDebuggerSessionEvent.ProcessStarted(sessionId, XDebuggerSessionInfoDto(session.sessionName)))
          }
        }

        override fun processStopped(debugProcess: XDebugProcess) {
          val session = debugProcess.session as? XDebugSessionImpl ?: return
          launch(dispatcher) {
            val sessionId = session.entity.await().sessionId
            send(XDebuggerSessionEvent.ProcessStopped(sessionId))
          }
        }

        override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
          val previousSession = previousSession as? XDebugSessionImpl ?: return
          val currentSession = currentSession as? XDebugSessionImpl ?: return
          launch(dispatcher) {
            val previousSessionId = previousSession.entity.await().sessionId
            val currentSessionId = currentSession.entity.await().sessionId
            send(XDebuggerSessionEvent.CurrentSessionChanged(previousSessionId, currentSessionId))
          }
        }
      })
      awaitClose()
    }.buffer(Channel.UNLIMITED)
  }
}