// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.findEditorOrNull
import com.intellij.platform.execution.impl.backend.createProcessHandlerDto
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.project.findProjectOrNull
import com.intellij.util.asDisposable
import com.intellij.xdebugger.*
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.XDebuggerUtilImpl.reshowInlayRunToCursor
import com.intellij.xdebugger.impl.XSteppingSuspendContext
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy.Companion.useFeProxy
import com.intellij.xdebugger.impl.rpc.*
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.rpc.models.getOrStoreGlobally
import com.intellij.xdebugger.impl.rpc.models.storeGlobally
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl
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
      currentSession?.id
    }
  }

  override suspend fun sessions(projectId: ProjectId): XDebugSessionsList {
    val project = projectId.findProject()
    val sessions = XDebuggerManager.getInstance(project).debugSessions.map { createSessionDto(it as XDebugSessionImpl, it.debugProcess) }
    return XDebugSessionsList(sessions, createSessionManagerEvents(projectId).toRpc())
  }

  private suspend fun createSessionDto(currentSession: XDebugSessionImpl, debugProcess: XDebugProcess): XDebugSessionDto {
    currentSession.sessionInitializedDeferred().await()
    val editorsProvider = debugProcess.editorsProvider
    val fileTypeId = editorsProvider.fileType.name
    val initialSessionState = XDebugSessionState(
      currentSession.isPaused, currentSession.isStopped, currentSession.isReadOnly, currentSession.isPauseActionSupported(), currentSession.isSuspended,
    )
    val sessionDataDto = XDebugSessionDataDto(
      currentSession.sessionData.configurationName,
      currentSession.areBreakpointsMuted(),
      currentSession.getBreakpointsMutedFlow().toRpc(),
    )

    val consoleView = if (useFeProxy()) {
      currentSession.consoleView!!.toRpc(currentSession.runContentDescriptor, debugProcess)
    }
    else {
      null
    }
    return XDebugSessionDto(
      currentSession.id,
      XDebuggerEditorsProviderDto(fileTypeId, editorsProvider),
      initialSessionState,
      currentSession.suspendData(),
      currentSession.sessionName,
      createSessionEvents(currentSession).toRpc(),
      sessionDataDto,
      consoleView,
      createProcessHandlerDto(currentSession.coroutineScope, currentSession.debugProcess.processHandler),
      debugProcess.smartStepIntoHandler?.let { XSmartStepIntoHandlerDto(it.popupTitle) },
      currentSession.debugProcess.isLibraryFrameFilterSupported,
    )
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun createSessionEvents(currentSession: XDebugSessionImpl): Flow<XDebuggerSessionEvent> = channelFlow {
    currentSession.addSessionListener(object : XDebugSessionListener {
      override fun sessionPaused() {
        val suspendContext = currentSession.suspendContext ?: return
        val suspendScope = currentSession.currentSuspendCoroutineScope ?: return
        val data = async {
          val suspendContextId = coroutineScope {
            suspendContext.storeGlobally(suspendScope, currentSession)
          }
          val suspendContextDto = XSuspendContextDto(suspendContextId, suspendContext is XSteppingSuspendContext)
          val executionStackDto = suspendContext.activeExecutionStack?.let {
            val activeExecutionStackId = it.getOrStoreGlobally(suspendScope, currentSession)
            XExecutionStackDto(activeExecutionStackId, it.displayName, it.icon?.rpcId())
          }
          val stackTraceDto = currentSession.currentStackFrame?.let {
            createXStackFrameDto(it, suspendScope, currentSession)
          }
          SuspendData(suspendContextDto,
                      executionStackDto,
                      stackTraceDto)
        }
        trySend(XDebuggerSessionEvent.SessionPaused(data))
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
        val suspendScope = currentSession.currentSuspendCoroutineScope ?: return
        val stackTraceDto = currentSession.currentStackFrame?.let {
          createXStackFrameDto(it, suspendScope, currentSession)
        }
        trySend(XDebuggerSessionEvent.StackFrameChanged(stackTraceDto))
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
          trySend(XDebuggerManagerSessionEvent.ProcessStopped(session.id))
        }

        override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
          val previousSessionId = (previousSession as? XDebugSessionImpl)?.id
          val currentSessionId = (currentSession as? XDebugSessionImpl)?.id
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

  override suspend fun sessionTabSelected(projectId: ProjectId, sessionId: XDebugSessionId?) {
    val project = projectId.findProjectOrNull() ?: return
    val session = if (sessionId == null) null else (sessionId.findValue() ?: return)
    val managerImpl = XDebuggerManagerImpl.getInstance(project) as XDebuggerManagerImpl
    managerImpl.onSessionSelected(session)
  }

  override suspend fun sessionTabClosed(sessionId: XDebugSessionId) {
    val session = sessionId.findValue() ?: return
    val managerImpl = XDebuggerManagerImpl.getInstance(session.project) as XDebuggerManagerImpl
    managerImpl.removeSessionNoNotify(session)
  }

  override suspend fun showLibraryFrames(show: Boolean) {
    XDebuggerSettingManagerImpl.getInstanceImpl().dataViewSettings.isShowLibraryStackFrames = show
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override suspend fun getBreakpoints(projectId: ProjectId): Flow<Set<XBreakpointDto>> {
    val project = projectId.findProject()
    val breakpointManager = XDebuggerManager.getInstance(project) as XDebuggerManagerImpl

    return breakpointManager.breakpointManager.allBreakpointsFlow.mapLatest { breakpoints ->
      breakpoints.mapTo(LinkedHashSet()) { breakpoint ->
        breakpoint.toRpc()
      }
    }
  }
}
