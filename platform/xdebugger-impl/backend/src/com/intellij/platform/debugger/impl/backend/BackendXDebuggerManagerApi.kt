// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.findEditorOrNull
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.project.findProjectOrNull
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.XDebuggerUtilImpl.reshowInlayRunToCursor
import com.intellij.xdebugger.impl.rpc.*
import fleet.rpc.core.toRpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class BackendXDebuggerManagerApi : XDebuggerManagerApi {
  @OptIn(ExperimentalCoroutinesApi::class)
  override suspend fun currentSession(projectId: ProjectId): Flow<XDebugSessionId?> {
    val project = projectId.findProject()

    return (XDebuggerManager.getInstance(project) as XDebuggerManagerImpl).currentSessionFlow.mapLatest { currentSession ->
      currentSession?.id()
    }
  }

  override suspend fun sessions(projectId: ProjectId): XDebugSessionsList {
    val project = projectId.findProject()
    val sessions = XDebuggerManager.getInstance(project).debugSessions.map { createSessionDto(it as XDebugSessionImpl, it.debugProcess) }
    return XDebugSessionsList(sessions, sessionEvents(projectId).toRpc())
  }

  private suspend fun createSessionDto(currentSession: XDebugSessionImpl, debugProcess: XDebugProcess): XDebugSessionDto {
    val editorsProvider = debugProcess.editorsProvider
    val fileTypeId = editorsProvider.fileType.name
    val initialSessionState = XDebugSessionState(
      currentSession.isPaused, currentSession.isStopped, currentSession.isReadOnly, currentSession.isPauseActionSupported(), currentSession.isSuspended,
    )
    return XDebugSessionDto(currentSession.id(), XDebuggerEditorsProviderDto(fileTypeId, editorsProvider), initialSessionState)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun sessionEvents(projectId: ProjectId): Flow<XDebuggerSessionEvent> {
    val project = projectId.findProject()
    val dispatcher = Dispatchers.Default.limitedParallelism(1)
    return channelFlow {
      project.messageBus.connect(this).subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
        override fun processStarted(debugProcess: XDebugProcess) {
          val session = debugProcess.session as? XDebugSessionImpl ?: return
          launch(dispatcher) {
            val sessionDto = createSessionDto(session, debugProcess)
            send(XDebuggerSessionEvent.ProcessStarted(sessionDto.id, sessionDto))
          }
        }

        override fun processStopped(debugProcess: XDebugProcess) {
          val session = debugProcess.session as? XDebugSessionImpl ?: return
          launch(dispatcher) {
            val sessionId = session.id()
            send(XDebuggerSessionEvent.ProcessStopped(sessionId))
          }
        }

        override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
          val previousSession = previousSession as? XDebugSessionImpl ?: return
          val currentSession = currentSession as? XDebugSessionImpl ?: return
          launch(dispatcher) {
            val previousSessionId = previousSession.id()
            val currentSessionId = currentSession.id()
            send(XDebuggerSessionEvent.CurrentSessionChanged(previousSessionId, currentSessionId))
          }
        }
      })
      awaitClose()
    }.buffer(Channel.UNLIMITED)
  }

  override suspend fun reshowInlays(projectId: ProjectId, editorId: EditorId?) {
    val project = projectId.findProjectOrNull() ?: return
    val editor = editorId?.findEditorOrNull() ?: return
    withContext(Dispatchers.EDT) {
      reshowInlayRunToCursor(project, editor)
    }
  }
}