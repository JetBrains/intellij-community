// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.findEditorOrNull
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.project.findProjectOrNull
import com.intellij.util.asDisposable
import com.intellij.xdebugger.*
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.XDebuggerUtilImpl.reshowInlayRunToCursor
import com.intellij.xdebugger.impl.rpc.*
import fleet.rpc.core.toRpc
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.mapLatest

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
    return XDebugSessionsList(sessions, createSessionManagerEvents(projectId).toRpc())
  }

  private suspend fun createSessionDto(currentSession: XDebugSessionImpl, debugProcess: XDebugProcess): XDebugSessionDto {
    // TODO: !!!really strong HACK to let consoleView to be initialized.
    //  It should be Deferred in this case
    //  or processStarted of DebugProcess listener should.
    repeat(20) {
      if (currentSession.consoleView == null) {
        delay(100)
      }
    }
    val editorsProvider = debugProcess.editorsProvider
    val fileTypeId = editorsProvider.fileType.name
    val initialSessionState = XDebugSessionState(
      currentSession.isPaused, currentSession.isStopped, currentSession.isReadOnly, currentSession.isPauseActionSupported(), currentSession.isSuspended,
    )
    val sessionDataDto = XDebugSessionDataDto(
      currentSession.sessionData.configurationName,
    )
    return XDebugSessionDto(
      currentSession.id(),
      XDebuggerEditorsProviderDto(fileTypeId, editorsProvider),
      initialSessionState,
      currentSession.sessionName,
      createSessionEvents(currentSession).toRpc(),
      sessionDataDto,
      currentSession.consoleView?.toRpc(debugProcess),
    )
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun createSessionEvents(currentSession: XDebugSessionImpl): Flow<XDebuggerSessionEvent> = channelFlow {
    currentSession.addSessionListener(object : XDebugSessionListener {
      override fun sessionPaused() {
        trySend(XDebuggerSessionEvent.SessionPaused())
      }

      override fun sessionResumed() {
        trySend(XDebuggerSessionEvent.SessionResumed())
      }

      override fun sessionStopped() {
        trySend(XDebuggerSessionEvent.SessionStopped())
      }

      override fun beforeSessionResume() {
        trySend(XDebuggerSessionEvent.BeforeSessionResume())
      }

      override fun stackFrameChanged() {
        trySend(XDebuggerSessionEvent.StackFrameChanged())
      }

      override fun settingsChanged() {
        trySend(XDebuggerSessionEvent.SettingsChanged())
      }

      override fun breakpointsMuted(muted: Boolean) {
        trySend(XDebuggerSessionEvent.BreakpointsMuted(muted))
      }
    }, this.asDisposable())
    awaitClose()
  }.buffer(Channel.UNLIMITED)

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun createSessionManagerEvents(projectId: ProjectId): Flow<XDebuggerManagerSessionEvent> {
    val project = projectId.findProject()
    return channelFlow {
      project.messageBus.connect(this).subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
        override fun processStarted(debugProcess: XDebugProcess) {
          val session = debugProcess.session as? XDebugSessionImpl ?: return
          launch {
            val sessionDto = createSessionDto(session, debugProcess)
            send(XDebuggerManagerSessionEvent.ProcessStarted(sessionDto.id, sessionDto))
          }
        }

        override fun processStopped(debugProcess: XDebugProcess) {
          val session = debugProcess.session as? XDebugSessionImpl ?: return
          trySend(XDebuggerManagerSessionEvent.ProcessStopped(session.idUnsafe))
        }

        override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
          val previousSessionId = (previousSession as? XDebugSessionImpl)?.idUnsafe
          val currentSessionId = (currentSession as? XDebugSessionImpl)?.idUnsafe
          trySend(XDebuggerManagerSessionEvent.CurrentSessionChanged(previousSessionId, currentSessionId))
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
