// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.ide.rpc.AnActionId
import com.intellij.ide.rpc.rpcId
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.findEditorOrNull
import com.intellij.platform.debugger.impl.rpc.XBreakpointEvent
import com.intellij.platform.debugger.impl.rpc.XBreakpointsSetDto
import com.intellij.platform.debugger.impl.rpc.XDebugSessionDataDto
import com.intellij.platform.debugger.impl.rpc.XDebugSessionDto
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.platform.debugger.impl.rpc.XDebugSessionState
import com.intellij.platform.debugger.impl.rpc.XDebugSessionsList
import com.intellij.platform.debugger.impl.rpc.XDebuggerEditorsProviderDto
import com.intellij.platform.debugger.impl.rpc.XDebuggerManagerApi
import com.intellij.platform.debugger.impl.rpc.XDebuggerManagerSessionEvent
import com.intellij.platform.debugger.impl.rpc.XDebuggerSessionEvent
import com.intellij.platform.debugger.impl.rpc.XFrontendDebuggerCapabilities
import com.intellij.platform.debugger.impl.rpc.XSmartStepIntoHandlerDto
import com.intellij.platform.debugger.impl.rpc.toRpc
import com.intellij.platform.execution.impl.backend.createProcessHandlerDto
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.project.findProjectOrNull
import com.intellij.util.asDisposable
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.XDebuggerUtilImpl.reshowInlayRunToCursor
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.rpc.models.storeGlobally
import com.intellij.xdebugger.impl.rpc.toRpc
import fleet.rpc.core.toRpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

internal class BackendXDebuggerManagerApi : XDebuggerManagerApi {
  override suspend fun initialize(projectId: ProjectId, capabilities: XFrontendDebuggerCapabilities) {
    val project = projectId.findProject()
    val manager = XDebuggerManager.getInstance(project) as XDebuggerManagerImpl
    val old = manager.frontendCapabilities
    val new = XFrontendDebuggerCapabilities(
      // _any_ of clients can show images
      canShowImages = old.canShowImages || capabilities.canShowImages,
    )
    manager.frontendCapabilities = new
  }

  override suspend fun sessions(projectId: ProjectId): XDebugSessionsList {
    val project = projectId.findProject()
    val sessions = XDebuggerManager.getInstance(project).debugSessions.map { createSessionDto(it as XDebugSessionImpl, it.debugProcess) }
    val initialSessions = sessions.map { it.id }.toSet()
    return XDebugSessionsList(sessions, createSessionManagerEvents(projectId, initialSessions).toRpc())
  }

  private suspend fun createSessionDto(currentSession: XDebugSessionImpl, debugProcess: XDebugProcess): XDebugSessionDto {
    currentSession.sessionInitializedDeferred().await()
    val initialSessionState = currentSession.state()
    val sessionDataDto = XDebugSessionDataDto(
      currentSession.sessionDataId,
      currentSession.sessionData.configurationName,
      currentSession.areBreakpointsMuted(),
      currentSession.getBreakpointsMutedFlow().toRpc(),
    )

    val consoleView = if (SplitDebuggerMode.isSplitDebugger()) {
      currentSession.consoleView?.toRpc(currentSession.tabCoroutineScope, debugProcess)
    }
    else {
      null
    }
    val activeBreakpointFlow = currentSession.activeNonLineBreakpointFlow.map {
      if (it !is XBreakpointBase<*, *, *>) return@map null
      it.breakpointId
    }.toRpc()
    val cs = currentSession.coroutineScope

    val (leftToolbarActions, topToolbarActions, settingsActions) = registerAdditionalActions(debugProcess, cs)

    return XDebugSessionDto(
      currentSession.id,
      currentSession.getMockRunContentDescriptorIfInitialized()?.id as RunContentDescriptorIdImpl?,
      debugProcess.editorsProvider.toRpc(cs),
      initialSessionState,
      currentSession.suspendData(),
      currentSession.sessionName,
      currentSession.getSessionEventsFlow(initialSessionState).toRpc(),
      sessionDataDto,
      consoleView,
      createProcessHandlerDto(cs, currentSession.debugProcess.processHandler),
      debugProcess.smartStepIntoHandler?.let { XSmartStepIntoHandlerDto(it.popupTitle) },
      currentSession.debugProcess.isLibraryFrameFilterSupported,
      currentSession.debugProcess.isValuesCustomSorted,
      activeBreakpointFlow,
      currentSession.restartActions.map { it.rpcId(cs) },
      currentSession.extraActions.map { it.rpcId(cs) },
      currentSession.extraStopActions.map { it.rpcId(cs) },
      leftToolbarActions,
      topToolbarActions,
      settingsActions,
      processDescriptor = debugProcess.processDescriptor?.asDeferred(),
    )
  }

  private fun registerAdditionalActions(
    debugProcess: XDebugProcess,
    cs: CoroutineScope,
  ): Triple<List<AnActionId>, List<AnActionId>, List<AnActionId>> {
    val left = DefaultActionGroup()
    val topLeft = DefaultActionGroup()
    val settings = DefaultActionGroup()
    debugProcess.registerAdditionalActions(left, topLeft, settings)
    return Triple(left.toRpc(cs), topLeft.toRpc(cs), settings.toRpc(cs))
  }

  private fun DefaultActionGroup.toRpc(cs: CoroutineScope): List<AnActionId> =
    childActionsOrStubs.mapNotNull { it?.rpcId(cs) }


  @OptIn(ExperimentalCoroutinesApi::class)
  private fun createSessionManagerEvents(projectId: ProjectId, initialSessionIds: Set<XDebugSessionId>): Flow<XDebuggerManagerSessionEvent> {
    val project = projectId.findProject()
    return channelFlow {
      val listener = object : XDebuggerManagerListener {
        override fun processStarted(debugProcess: XDebugProcess) {
          val session = debugProcess.session as? XDebugSessionImpl ?: return
          launch {
            val sessionDto = createSessionDto(session, debugProcess)
            send(XDebuggerManagerSessionEvent.ProcessStarted(sessionDto))
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
      }
      project.messageBus.connect(this).subscribe(XDebuggerManager.TOPIC, listener)
      val currentSessions = XDebuggerManager.getInstance(project).debugSessions.filterIsInstance<XDebugSessionImpl>().associateBy { it.id }
      val newlyAddedSessions = currentSessions.keys - initialSessionIds
      val completedSessions = initialSessionIds - currentSessions.keys

      for (newlyAddedSession in newlyAddedSessions) {
        val session = currentSessions[newlyAddedSession] ?: continue
        listener.processStarted(session.debugProcess)
      }
      for (completedSession in completedSessions) {
        send(XDebuggerManagerSessionEvent.ProcessStopped(completedSession))
      }

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
  override suspend fun getBreakpoints(projectId: ProjectId): XBreakpointsSetDto {
    val project = projectId.findProject()
    val breakpointManager = (XDebuggerManager.getInstance(project) as XDebuggerManagerImpl).breakpointManager

    val initialBreakpoints = breakpointManager.allBreakpoints.mapTo(LinkedHashSet()) {
      it.toRpc()
    }

    val initialBreakpointIds = initialBreakpoints.map { it.id }

    val eventsFlow = channelFlow {
      val events = Channel<suspend () -> XBreakpointEvent>(Channel.UNLIMITED)
      project.messageBus.connect(this@channelFlow).subscribe(XBreakpointListener.TOPIC, object : XBreakpointListener<XBreakpoint<*>> {
        override fun breakpointAdded(breakpoint: XBreakpoint<*>) {
          events.trySend {
            XBreakpointEvent.BreakpointAdded((breakpoint as XBreakpointBase<*, *, *>).toRpc())
          }
        }

        override fun breakpointRemoved(breakpoint: XBreakpoint<*>) {
          events.trySend {
            XBreakpointEvent.BreakpointRemoved((breakpoint as XBreakpointBase<*, *, *>).breakpointId)
          }
        }

        override fun breakpointPresentationUpdated(breakpoint: XBreakpoint<*>, session: XDebugSession?) {
          val breakpointBase = breakpoint as? XBreakpointBase<*, *, *> ?: return
          val sessionImpl = session as? XDebugSessionImpl

          events.trySend {
            XBreakpointEvent.BreakpointPresentationUpdated(
              breakpointId = breakpointBase.breakpointId,
              customPresentation = breakpointBase.customizedPresentation?.toRpc(),
              currentSessionCustomPresentation = sessionImpl?.getBreakpointPresentation(breakpoint)?.toRpc()
            )
          }
        }
      })

      val currentBreakpoints = breakpointManager.allBreakpoints
      val currentBreakpointIds = currentBreakpoints.map { it.breakpointId }.toSet()

      val createdDuringSubscription = currentBreakpoints.filter { it.breakpointId !in initialBreakpointIds }
      for (breakpoint in createdDuringSubscription) {
        events.trySend {
          XBreakpointEvent.BreakpointAdded(breakpoint.toRpc())
        }
      }

      val removedDuringSubscription = initialBreakpointIds - currentBreakpointIds
      for (breakpointIdToRemove in removedDuringSubscription) {
        events.trySend {
          XBreakpointEvent.BreakpointRemoved(breakpointIdToRemove)
        }
      }

      for (event in events) {
        send(event())
      }
    }

    return XBreakpointsSetDto(initialBreakpoints, eventsFlow.toRpc())
  }
}

internal fun XDebuggerEditorsProvider.toRpc(cs: CoroutineScope): XDebuggerEditorsProviderDto {
  val id = storeGlobally(cs)
  return XDebuggerEditorsProviderDto(id, fileType.name, this)
}

@OptIn(ExperimentalCoroutinesApi::class)
@ApiStatus.Internal
fun XDebugSessionImpl.getSessionEventsFlow(
  initialSessionState: XDebugSessionState = state(),
): Flow<XDebuggerSessionEvent> = channelFlow {
  val currentSession = this@getSessionEventsFlow
  // Offload serialization from listener to background
  val rawEvents = Channel<suspend () -> XDebuggerSessionEvent>(Channel.UNLIMITED)

  val listener = object : XDebugSessionListener {
    override fun sessionPaused() {
      rawEvents.trySend { XDebuggerSessionEvent.SessionPaused(currentSession.state(), currentSession.suspendData()) }
    }

    override fun sessionResumed() {
      rawEvents.trySend { XDebuggerSessionEvent.SessionResumed(currentSession.state()) }
    }

    override fun sessionStopped() {
      rawEvents.trySend { XDebuggerSessionEvent.SessionStopped(currentSession.state()) }
    }

    override fun beforeSessionResume() {
      rawEvents.trySend { XDebuggerSessionEvent.BeforeSessionResume(currentSession.state()) }
    }

    override fun stackFrameChanged() {
      val suspendScope = currentSession.getSuspendContextModel()?.coroutineScope ?: return
      rawEvents.trySend {
        val stackFrameDto = currentSession.currentStackFrame?.toRpc(suspendScope, currentSession)
        XDebuggerSessionEvent.StackFrameChanged(
          currentSession.state(),
          currentSession.currentPosition?.toRpc(),
          currentSession.topFramePosition?.toRpc(),
          currentSession.isTopFrameSelected,
          stackFrameDto,
        )
      }
    }

    override fun stackFrameChanged(changedByUser: Boolean) {
      // Ignore changes from the frontend side, they're already handled in FrontendXDebuggerSession
      if (!changedByUser) {
        stackFrameChanged()
      }
    }

    override fun settingsChanged() {
      rawEvents.trySend { XDebuggerSessionEvent.SettingsChanged(currentSession.state()) }
    }

    override fun settingsChangedFromFrontend() {
      // Ignore changes from the frontend side, they're already handled in FrontendXDebuggerSession
    }

    override fun breakpointsMuted(muted: Boolean) {
      rawEvents.trySend { XDebuggerSessionEvent.BreakpointsMuted(muted) }
    }
  }
  currentSession.addSessionListener(listener, this.asDisposable())
  // Try to send the important events lost during listener installation
  if (currentSession.isStopped && !initialSessionState.isStopped) {
    listener.sessionStopped()
  }
  else if (currentSession.isPaused && !initialSessionState.isPaused) {
    listener.sessionPaused()
  }
  else if (!currentSession.isPaused && initialSessionState.isPaused) {
    listener.sessionResumed()
  }

  rawEvents.consumeEach { eventProducer ->
    val element = fileLogger().runAndLogException {
      eventProducer()
    } ?: return@consumeEach
    send(element)
  }
}.buffer()

private fun XDebugSessionImpl.state(): XDebugSessionState = XDebugSessionState(
  isPaused = isPaused,
  isStopped = isStopped,
  isReadOnly = isReadOnly,
  isPauseActionSupported = isPauseActionSupported(),
  isSuspended = isSuspended,
  isStepOverActionAllowed = isStepOverActionAllowed,
  isStepOutActionAllowed = isStepOutActionAllowed,
  isRunToCursorActionAllowed = isRunToCursorActionAllowed,
)
