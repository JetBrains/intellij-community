// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironmentProxy
import com.intellij.execution.ui.ConsoleView
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXDebuggerEvaluator
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue
import com.intellij.platform.debugger.impl.frontend.frame.FrontendDropFrameHandler
import com.intellij.platform.debugger.impl.frontend.frame.FrontendXExecutionStack
import com.intellij.platform.debugger.impl.frontend.frame.FrontendXStackFrame
import com.intellij.platform.debugger.impl.frontend.frame.FrontendXSuspendContext
import com.intellij.platform.debugger.impl.frontend.storage.FrontendXStackFramesStorage
import com.intellij.platform.debugger.impl.frontend.storage.getOrCreateStackFrame
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.platform.execution.impl.frontend.createFrontendProcessHandler
import com.intellij.platform.execution.impl.frontend.executionEnvironment
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.EventDispatcher
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XDropFrameHandler
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XSourceKind
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy
import com.intellij.xdebugger.impl.frame.*
import com.intellij.xdebugger.impl.inline.DebuggerInlayListener
import com.intellij.xdebugger.impl.rpc.*
import com.intellij.xdebugger.impl.ui.XDebugSessionData
import com.intellij.xdebugger.impl.ui.XDebugSessionTab
import com.intellij.xdebugger.ui.XDebugTabLayouter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.event.HyperlinkListener

@VisibleForTesting
@ApiStatus.Internal
class FrontendXDebuggerSession private constructor(
  override val project: Project,
  scope: CoroutineScope,
  private val manager: FrontendXDebuggerManager,
  sessionDto: XDebugSessionDto,
  override val processHandler: ProcessHandler,
  override val consoleView: ConsoleView?,
) : XDebugSessionProxy {
  private val cs = scope.childScope("Session ${sessionDto.id}")
  private val localEditorsProvider = sessionDto.editorsProviderDto.editorsProvider
  private val eventsDispatcher = EventDispatcher.create(XDebugSessionListener::class.java)
  override val id: XDebugSessionId = sessionDto.id

  // TODO merge sourcePosition, topSourcePosition, sessionState with suspendContext flow
  // TODO to be sure that the state is not out of sync
  private val sourcePosition: StateFlow<XSourcePosition?> =
    cs.createPositionFlow { XDebugSessionApi.getInstance().currentSourcePosition(id) }

  private val topSourcePosition: StateFlow<XSourcePosition?> =
    cs.createPositionFlow { XDebugSessionApi.getInstance().topSourcePosition(id) }

  private val sessionState: StateFlow<XDebugSessionState> =
    channelFlow {
      XDebugSessionApi.getInstance().currentSessionState(id).collectLatest { sessionState ->
        send(sessionState)
      }
    }.stateIn(cs, SharingStarted.Eagerly, sessionDto.initialSessionState)

  private val suspendContext = MutableStateFlow<FrontendXSuspendContext?>(null)
  private val currentExecutionStack = MutableStateFlow<FrontendXExecutionStack?>(null)
  private val currentStackFrame = MutableStateFlow<FrontendXStackFrame?>(null)
  private val activeNonLineBreakpoint: StateFlow<XBreakpointProxy?> = channelFlow {
    sessionDto.activeNonLineBreakpointIdFlow.toFlow().collectLatest { breakpointId ->
      if (breakpointId == null) {
        send(null)
        return@collectLatest
      }
      val breakpoint = FrontendXDebuggerManager.getInstance(project).breakpointsManager.getBreakpointById(breakpointId)
      send(breakpoint)
    }
  }.stateIn(cs, SharingStarted.Eagerly, null)

  // TODO Actually session could have a global evaluator, see
  //  com.intellij.xdebugger.XDebugProcess.getEvaluator overrides
  override val currentEvaluator: XDebuggerEvaluator?
    get() = currentStackFrame.value?.evaluator

  override val isStopped: Boolean
    get() = sessionState.value.isStopped

  override val isPaused: Boolean
    get() = sessionState.value.isPaused

  override val environmentProxy: ExecutionEnvironmentProxy?
    get() = null // TODO: implement!

  override val isReadOnly: Boolean
    get() = sessionState.value.isReadOnly

  override val isPauseActionSupported: Boolean
    get() = sessionState.value.isPauseActionSupported

  override val isSuspended: Boolean
    get() = sessionState.value.isSuspended

  override val editorsProvider: XDebuggerEditorsProvider =
    localEditorsProvider
    ?: FrontendXDebuggerEditorsProvider(sessionDto.editorsProviderDto.fileTypeId,
                                        documentIdProvider = { frontendDocumentId, expression, position, mode ->
                                          XDebugSessionApi.getInstance().createDocument(frontendDocumentId, id, expression, position, mode)
                                        })

  override val isLibraryFrameFilterSupported: Boolean = sessionDto.isLibraryFrameFilterSupported

  override val valueMarkers: XValueMarkers<FrontendXValue, XValueMarkerId> = FrontendXValueMarkers(project)

  private var _sessionTab: XDebugSessionTab? = null
  override val sessionTab: XDebugSessionTab?
    get() = _sessionTab

  // TODO all of the methods below
  // TODO pass in DTO?
  override val sessionName: String = sessionDto.sessionName
  override val sessionData: XDebugSessionData = FrontendXDebugSessionData(project, sessionDto.sessionDataDto,
                                                                          cs, id, sessionState)

  override val restartActions: List<AnAction>
    get() = emptyList() // TODO
  override val extraActions: List<AnAction>
    get() = emptyList() // TODO
  override val extraStopActions: List<AnAction>
    get() = emptyList() // TODO
  override val coroutineScope: CoroutineScope = cs
  override val currentStateMessage: String
    get() = if (isStopped) XDebuggerBundle.message("debugger.state.message.disconnected") else XDebuggerBundle.message("debugger.state.message.connected") // TODO
  override val currentStateHyperlinkListener: HyperlinkListener?
    get() = null // TODO

  override val smartStepIntoHandlerEntry: XSmartStepIntoHandlerEntry? = sessionDto.smartStepIntoHandlerDto?.let {
    object : XSmartStepIntoHandlerEntry {
      override val popupTitle: String
        get() = it.title
    }
  }

  override val currentSuspendContextCoroutineScope: CoroutineScope?
    get() = suspendContext.value?.lifetimeScope

  private val dropFrameHandler = FrontendDropFrameHandler(id, scope)

  init {
    DebuggerInlayListener.getInstance(project).startListening()
    sessionDto.initialSuspendData?.applyToCurrents()
    cs.launch {
      sessionDto.sessionEvents.toFlow().collect { event ->
        with(event) {
          updateCurrents()
          dispatch()
        }
      }
    }
    cs.launch {
      currentStackFrame.collectLatest {
        withContext(Dispatchers.EDT) {
          eventsDispatcher.multicaster.stackFrameChanged()
        }
      }
    }
    cs.launch {
      XDebugSessionTabApi.getInstance().sessionTabInfo(id).collectLatest { tabDto ->
        if (tabDto == null) return@collectLatest
        initTabInfo(tabDto)
        this.cancel() // Only one tab expected
      }
    }
  }

  private suspend fun XDebuggerSessionEvent.updateCurrents() {
    when (this) {
      is XDebuggerSessionEvent.SessionPaused -> {
        suspendData.await()?.applyToCurrents()
      }
      is XDebuggerSessionEvent.SessionResumed,
      is XDebuggerSessionEvent.BeforeSessionResume,
        -> {
        suspendContext.getAndUpdate { null }?.cancel()
        currentExecutionStack.value = null
        currentStackFrame.value = null
      }
      is XDebuggerSessionEvent.SessionStopped -> {
        cs.cancel()
        suspendContext.value = null
        currentExecutionStack.value = null
        currentStackFrame.value = null
      }
      is XDebuggerSessionEvent.StackFrameChanged -> {
        stackFrame?.await()?.let {
          val suspendContext = suspendContext.value ?: return
          currentStackFrame.value = suspendContext.getOrCreateStackFrame(it)
        }
      }
      else -> {}
    }
  }

  private fun SuspendData.applyToCurrents() {
    val (suspendContextDto, executionStackDto, stackFrameDto) = this
    val oldSuspendContext = suspendContext.value
    if (oldSuspendContext == null || suspendContextDto.id != oldSuspendContext.id) {
      val suspendContextLifetimeScope = cs.childScope("${cs.coroutineContext[CoroutineName]} (context ${suspendContextDto.id})",
                                                      FrontendXStackFramesStorage())
      val currentSuspendContext = FrontendXSuspendContext(suspendContextDto, project, suspendContextLifetimeScope)
      suspendContext.getAndUpdate { currentSuspendContext }?.cancel()
    }
    val suspendContextLifetimeScope = suspendContext.value?.lifetimeScope ?: return

    executionStackDto?.let {
      currentExecutionStack.value = FrontendXExecutionStack(executionStackDto, project, suspendContextLifetimeScope).also {
        suspendContext.value?.activeExecutionStack = it
      }
    }
    stackFrameDto?.let {
      currentStackFrame.value = suspendContextLifetimeScope.getOrCreateStackFrame(it, project)
    }
  }

  private fun XDebuggerSessionEvent.dispatch() {
    when (this) {
      is XDebuggerSessionEvent.BeforeSessionResume -> eventsDispatcher.multicaster.beforeSessionResume()
      is XDebuggerSessionEvent.BreakpointsMuted -> eventsDispatcher.multicaster.breakpointsMuted(muted)
      is XDebuggerSessionEvent.SessionPaused -> eventsDispatcher.multicaster.sessionPaused()
      is XDebuggerSessionEvent.SessionResumed -> eventsDispatcher.multicaster.sessionResumed()
      is XDebuggerSessionEvent.SessionStopped -> eventsDispatcher.multicaster.sessionStopped()
      is XDebuggerSessionEvent.SettingsChanged -> eventsDispatcher.multicaster.settingsChanged()
      is XDebuggerSessionEvent.StackFrameChanged -> {
        // Do nothing, use stack frame update as the source of truth instead
      }
    }
  }

  @OptIn(AwaitCancellationAndInvoke::class)
  private fun initTabInfo(tabDto: XDebuggerSessionTabDto) {
    val (tabInfo, pausedFlow) = tabDto
    cs.launch {
      if (tabInfo !is XDebuggerSessionTabInfo) return@launch

      val proxy = this@FrontendXDebuggerSession
      withContext(Dispatchers.EDT) {
        XDebugSessionTab.create(proxy, tabInfo.iconId?.icon(), tabInfo.executionEnvironmentProxyDto?.executionEnvironment(project, cs), tabInfo.contentToReuse,
                                tabInfo.forceNewDebuggerUi, tabInfo.withFramesCustomization).apply {
          _sessionTab = this
          proxy.onTabInitialized(this)
          showTab()
          runContentDescriptor?.coroutineScope?.awaitCancellationAndInvoke {
            tabInfo.tabClosedCallback.send(Unit)
          }
          pausedFlow.toFlow().collectLatest { paused ->
            if (paused == null) return@collectLatest
            withContext(Dispatchers.EDT) {
              onPause(paused.pausedByUser, paused.topFrameIsAbsent)
            }
          }
        }
      }
    }
  }

  override fun getCurrentPosition(): XSourcePosition? = sourcePosition.value

  override fun getTopFramePosition(): XSourcePosition? = topSourcePosition.value

  override fun getFrameSourcePosition(frame: XStackFrame): XSourcePosition? {
    // TODO Support XSourceKind
    return frame.sourcePosition
  }

  override fun getFrameSourcePosition(frame: XStackFrame, sourceKind: XSourceKind): XSourcePosition? {
    // TODO Support XSourceKind
    return frame.sourcePosition
  }

  override fun getCurrentExecutionStack(): XExecutionStack? {
    return currentExecutionStack.value
  }

  override fun getCurrentStackFrame(): XStackFrame? {
    return currentStackFrame.value
  }

  override fun setCurrentStackFrame(executionStack: XExecutionStack, frame: XStackFrame, isTopFrame: Boolean) {
    cs.launch {
      currentExecutionStack.value = executionStack as FrontendXExecutionStack
      currentStackFrame.value = frame as FrontendXStackFrame
      XDebugSessionApi.getInstance().setCurrentStackFrame(id, executionStack.id,
                                                          frame.id, isTopFrame, changedByUser = true)
    }
  }

  override fun hasSuspendContext(): Boolean {
    return suspendContext.value != null
  }

  override fun isSteppingSuspendContext(): Boolean {
    val currentContext = suspendContext.value ?: return false
    return currentContext.isStepping
  }

  override fun computeExecutionStacks(provideContainer: () -> XSuspendContext.XExecutionStackContainer) {
    suspendContext.value?.computeExecutionStacks(provideContainer())
  }

  override fun createTabLayouter(): XDebugTabLayouter {
    return object : XDebugTabLayouter() {} // TODO
  }

  override fun addSessionListener(listener: XDebugSessionListener, disposable: Disposable) {
    eventsDispatcher.addListener(listener, disposable)
  }

  override fun rebuildViews() {
    cs.launch {
      XDebugSessionApi.getInstance().triggerUpdate(id)
    }
  }

  override fun registerAdditionalActions(leftToolbar: DefaultActionGroup, topLeftToolbar: DefaultActionGroup, settings: DefaultActionGroup) {
    // TODO
  }

  override fun putKey(sink: DataSink) {
    // do nothing, proxy is already set in tab
  }

  override fun updateExecutionPosition() {
    cs.launch {
      XDebugSessionApi.getInstance().updateExecutionPosition(id)
    }
  }

  override fun onTabInitialized(tab: XDebugSessionTab) {
    cs.launch {
      XDebugSessionTabApi.getInstance().onTabInitialized(id, XDebuggerSessionTabInfoCallback(tab))
    }
  }

  override fun createFileColorsCache(framesList: XDebuggerFramesList): XStackFramesListColorsCache {
    return FrontendXStackFramesListColorsCache(this, framesList)
  }

  override fun areBreakpointsMuted(): Boolean {
    return sessionData.isBreakpointsMuted
  }

  override fun muteBreakpoints(value: Boolean) {
    // Optimistic update
    sessionData.isBreakpointsMuted = value
    manager.breakpointsManager.getLineBreakpointManager().queueAllBreakpointsUpdate()
  }

  override fun isInactiveSlaveBreakpoint(breakpoint: XBreakpointProxy): Boolean {
    // TODO: support dependent manager
    return false
  }

  override fun getDropFrameHandler(): XDropFrameHandler = dropFrameHandler

  override fun getActiveNonLineBreakpoint(): XBreakpointProxy? {
    return activeNonLineBreakpoint.value
  }

  companion object {
    private val LOG = thisLogger()

    suspend fun create(
      project: Project,
      scope: CoroutineScope,
      manager: FrontendXDebuggerManager,
      sessionDto: XDebugSessionDto,
    ): FrontendXDebuggerSession {
      val processHandler = createFrontendProcessHandler(project, sessionDto.processHandlerDto)
      val consoleView = sessionDto.consoleViewData?.consoleView(processHandler)

      return FrontendXDebuggerSession(project, scope, manager, sessionDto, processHandler, consoleView)
    }
  }
}

private fun CoroutineScope.createPositionFlow(dtoFlow: suspend () -> Flow<XSourcePositionDto?>): StateFlow<XSourcePosition?> = channelFlow {
  dtoFlow().collectLatest { sourcePositionDto ->
    if (sourcePositionDto == null) {
      send(null)
      return@collectLatest
    }
    supervisorScope {
      send(sourcePositionDto.sourcePosition())
      awaitCancellation()
    }
  }
}.stateIn(this, SharingStarted.Eagerly, null)

/**
 * Note that data added to [com.intellij.openapi.util.UserDataHolder] is not synced with backend.
 */
private class FrontendXDebugSessionData(
  project: Project, sessionDataDto: XDebugSessionDataDto,
  cs: CoroutineScope,
  sessionId: XDebugSessionId,
  sessionStateFlow: StateFlow<XDebugSessionState>,
) : XDebugSessionData(project, sessionDataDto.configurationName) {
  private val muteBreakpointsBackendSyncFlow = MutableSharedFlow<Boolean>(extraBufferCapacity = 1,
                                                                          onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    // Do not change to Kotlin setters, it calls this instead of super for some reason KT-76972
    super.setBreakpointsMuted(sessionDataDto.initialBreakpointsMuted)
    cs.syncWithLocalFlow(sessionDataDto.breakpointsMutedFlow.toFlow()) { super.setBreakpointsMuted(it) }

    super.setPauseSupported(sessionStateFlow.value.isPauseActionSupported)
    cs.syncWithLocalFlow(sessionStateFlow.map { it.isPauseActionSupported }) { super.setPauseSupported(it) }

    cs.launch {
      muteBreakpointsBackendSyncFlow.collectLatest { muted ->
        XDebugSessionApi.getInstance().muteBreakpoints(sessionId, muted)
      }
    }
  }

  override fun setBreakpointsMuted(muted: Boolean) {
    super.setBreakpointsMuted(muted)
    muteBreakpointsBackendSyncFlow.tryEmit(muted)
  }
}

private fun <T> CoroutineScope.syncWithLocalFlow(sourceFlow: Flow<T>, localFlowSetter: (T) -> Unit) {
  launch {
    sourceFlow.collectLatest { e ->
      localFlowSetter(e)
    }
  }
}

