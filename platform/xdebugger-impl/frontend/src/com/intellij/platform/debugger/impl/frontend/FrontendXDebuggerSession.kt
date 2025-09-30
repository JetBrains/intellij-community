// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.ide.rpc.action
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue
import com.intellij.platform.debugger.impl.frontend.frame.FrontendDropFrameHandler
import com.intellij.platform.debugger.impl.frontend.frame.FrontendXExecutionStack
import com.intellij.platform.debugger.impl.frontend.frame.FrontendXStackFrame
import com.intellij.platform.debugger.impl.frontend.frame.FrontendXSuspendContext
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
import com.intellij.xdebugger.impl.ui.SplitDebuggerUIUtil
import com.intellij.xdebugger.impl.ui.XDebugSessionData
import com.intellij.xdebugger.impl.ui.XDebugSessionTab
import com.intellij.xdebugger.impl.util.MonolithUtils
import com.intellij.xdebugger.ui.XDebugTabLayouter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.event.HyperlinkListener
import kotlin.time.Duration.Companion.seconds

private data class StackFrameUpdate(val frame: FrontendXStackFrame?, val isFrameChanged: Boolean)

@VisibleForTesting
@ApiStatus.Internal
class FrontendXDebuggerSession private constructor(
  override val project: Project,
  scope: CoroutineScope,
  private val manager: FrontendXDebuggerManager,
  private val sessionDto: XDebugSessionDto,
  override val processHandler: ProcessHandler,
  override val consoleView: ConsoleView?,
) : XDebugSessionProxy {
  private val cs = scope.childScope("Session ${sessionDto.id}")
  private val tabScope = scope.childScope("Session tab ${sessionDto.id}")

  @Volatile
  private var tabInfoInitialized = false

  private val eventsDispatcher = EventDispatcher.create(XDebugSessionListener::class.java)
  override val runContentDescriptorId: RunContentDescriptorIdImpl? = sessionDto.runContentDescriptorId
  override val id: XDebugSessionId = sessionDto.id

  private val sourcePositionFlow = MutableStateFlow<XSourcePosition?>(null)
  private val topSourcePositionFlow = MutableStateFlow<XSourcePosition?>(null)
  private val sessionStateFlow = MutableStateFlow(sessionDto.initialSessionState)
  private val suspendContext = MutableStateFlow<FrontendXSuspendContext?>(null)
  private val currentExecutionStack = MutableStateFlow<FrontendXExecutionStack?>(null)
  private val currentStackFrame = MutableStateFlow<StackFrameUpdate>(StackFrameUpdate(null, false))
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
    get() = currentStackFrame.value.frame?.evaluator

  override val isStopped: Boolean
    get() = sessionStateFlow.value.isStopped

  override val isPaused: Boolean
    get() = sessionStateFlow.value.isPaused

  override val isReadOnly: Boolean
    get() = sessionStateFlow.value.isReadOnly

  override val isPauseActionSupported: Boolean
    get() = sessionStateFlow.value.isPauseActionSupported

  override val isStepOverActionAllowed: Boolean
    get() = sessionStateFlow.value.isStepOverActionAllowed

  override val isStepOutActionAllowed: Boolean
    get() = sessionStateFlow.value.isStepOutActionAllowed

  override val isRunToCursorActionAllowed: Boolean
    get() = sessionStateFlow.value.isRunToCursorActionAllowed

  override val isSuspended: Boolean
    get() = sessionStateFlow.value.isSuspended

  override val editorsProvider: XDebuggerEditorsProvider = getEditorsProvider(
    cs, sessionDto.editorsProviderDto, documentIdProvider = { frontendDocumentId, expression, position, mode ->
    XDebugSessionApi.getInstance().createDocument(frontendDocumentId, sessionDto.id, expression, position, mode)
  })

  override val isLibraryFrameFilterSupported: Boolean = sessionDto.isLibraryFrameFilterSupported

  override val isValuesCustomSorted: Boolean = sessionDto.isValuesCustomSorted

  override val valueMarkers: XValueMarkers<FrontendXValue, XValueMarkerId> = FrontendXValueMarkers(project)

  private val sessionTabDeferred = CompletableDeferred<XDebugSessionTab>()

  @OptIn(ExperimentalCoroutinesApi::class)
  override val sessionTab: XDebugSessionTab?
    get() = if (sessionTabDeferred.isCompleted) sessionTabDeferred.getCompleted() else null

  override val sessionTabWhenInitialized: Deferred<XDebugSessionTab>
    get() = sessionTabDeferred

  override val sessionName: String = sessionDto.sessionName
  override val sessionData: XDebugSessionData = FrontendXDebugSessionData(project, sessionDto.sessionDataDto, tabScope, sessionStateFlow)

  override val restartActions: List<AnAction>
    get() = sessionDto.restartActions.mapNotNull { it.action() }
  override val extraActions: List<AnAction>
    get() = sessionDto.extraActions.mapNotNull { it.action() }
  override val extraStopActions: List<AnAction>
    get() = sessionDto.extraStopActions.mapNotNull { it.action() }
  override val consoleActions: List<AnAction>
    get() = sessionDto.consoleViewData?.actionIds()?.mapNotNull { it.action() }
          ?: consoleView?.createConsoleActions()?.toList()
          ?: emptyList()
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
      currentStackFrame.collectLatest { value ->
        if (value.isFrameChanged) {
          withContext(Dispatchers.EDT) {
            eventsDispatcher.multicaster.stackFrameChanged()
          }
        }
      }
    }
    tabScope.launch {
      val tabDto = XDebugSessionTabApi.getInstance().sessionTabInfo(sessionDto.sessionDataDto.id)
                     .firstOrNull() ?: return@launch
      tabInfoInitialized = true
      initTabInfo(tabDto)
    }
  }

  private suspend fun XDebuggerSessionEvent.updateCurrents() {
    when (this) {
      is XDebuggerSessionEvent.SessionPaused -> {
        updateState()
        suspendData.await()?.applyToCurrents()
      }
      is XDebuggerSessionEvent.SessionResumed, is XDebuggerSessionEvent.BeforeSessionResume -> {
        updateState()
        clearSuspendContext()
      }
      is XDebuggerSessionEvent.SessionStopped -> {
        cs.cancel()
        tabScope.launch {
          // Do not cancel the tab scope immediately, as it may be created with a delay
          delay(3.seconds)
          if (!tabInfoInitialized) {
            // tab was not created during session running
            tabScope.cancel()
          }
        }
        updateState()
        clearSuspendContext()
      }
      is XDebuggerSessionEvent.StackFrameChanged -> {
        updateState()
        sourcePositionFlow.value = sourcePosition?.sourcePosition()
        stackFrame?.await()?.let {
          val suspendContext = suspendContext.value ?: return
          currentStackFrame.value = StackFrameUpdate(suspendContext.getOrCreateStackFrame(it), true)
        }
      }
      is XDebuggerSessionEvent.BreakpointsMuted -> {}
      XDebuggerSessionEvent.SettingsChanged -> {}
    }
  }

  private fun XDebuggerSessionEvent.EventWithState.updateState() {
    sessionStateFlow.value = state
  }

  private fun clearSuspendContext() {
    suspendContext.getAndUpdate { null }?.cancel()
    sourcePositionFlow.value = null
    topSourcePositionFlow.value = null
    currentExecutionStack.value = null
    currentStackFrame.value = StackFrameUpdate(null, false)
  }

  private fun SuspendData.applyToCurrents() {
    val (suspendContextDto, executionStackDto, stackFrameDto, sourcePositionDto, topSourcePositionDto) = this
    val oldSuspendContext = suspendContext.value
    if (oldSuspendContext == null || suspendContextDto.id != oldSuspendContext.id) {
      val newSuspendContext = FrontendXSuspendContext(suspendContextDto, project, cs)
      val previousSuspendContext = suspendContext.getAndUpdate { newSuspendContext }
      previousSuspendContext?.cancel()
    }
    val suspendContextLifetimeScope = suspendContext.value?.lifetimeScope ?: return

    executionStackDto?.let {
      currentExecutionStack.value = FrontendXExecutionStack(executionStackDto, project, suspendContextLifetimeScope).also {
        suspendContext.value?.activeExecutionStack = it
      }
    }
    stackFrameDto?.let {
      currentStackFrame.value = StackFrameUpdate(suspendContextLifetimeScope.getOrCreateStackFrame(it, project), false)
    }
    sourcePositionFlow.value = sourcePositionDto?.sourcePosition()
    topSourcePositionFlow.value = topSourcePositionDto?.sourcePosition()
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
    tabScope.launch {
      if (tabInfo !is XDebuggerSessionTabInfo) return@launch

      val backendRunContentDescriptorId = tabInfo.backendRunContendDescriptorId.await()
      val executionEnvironmentId = tabInfo.executionEnvironmentId

      val proxy = this@FrontendXDebuggerSession
      withContext(Dispatchers.EDT) {
        // TODO restore content to reuse on frontend if needed (it is not used now in create)
        XDebugSessionTab.create(proxy, tabInfo.iconId?.icon(), tabInfo.executionEnvironmentProxyDto?.executionEnvironment(project, tabScope), null,
                                tabInfo.forceNewDebuggerUi, tabInfo.withFramesCustomization, tabInfo.defaultFramesViewKey).apply {
          setAdditionalKeysProvider { sink ->
            sink[SplitDebuggerUIUtil.SPLIT_RUN_CONTENT_DESCRIPTOR_KEY] = backendRunContentDescriptorId
            if (executionEnvironmentId != null) {
              sink[SplitDebuggerUIUtil.SPLIT_EXECUTION_ENVIRONMENT_KEY] = executionEnvironmentId
            }
          }
          sessionTabDeferred.complete(this)
          proxy.onTabInitialized(this)
          showTab()
          val descriptorScope = runContentDescriptor?.coroutineScope
          // don't subscribe on additional tabs if we have [ExecutionEnvironment] (it means this is Monolith)
          if (descriptorScope != null && tabInfo.executionEnvironmentProxyDto?.executionEnvironment == null) {
            subscribeOnAdditionalTabs(descriptorScope, this@apply, tabInfo.additionalTabsComponentManagerId)
          }
          descriptorScope?.awaitCancellationAndInvoke {
            tabInfo.tabClosedCallback.send(Unit)
            tabInfo.tabClosedCallback.close()
            tabScope.cancel()
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

  override fun getCurrentPosition(): XSourcePosition? = sourcePositionFlow.value

  override fun getTopFramePosition(): XSourcePosition? = topSourcePositionFlow.value

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
    return currentStackFrame.value.frame
  }

  override fun setCurrentStackFrame(executionStack: XExecutionStack, frame: XStackFrame, isTopFrame: Boolean) {
    cs.launch {
      currentExecutionStack.value = executionStack as FrontendXExecutionStack
      currentStackFrame.value = StackFrameUpdate(frame as FrontendXStackFrame, true)

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
    // Additional tabs are not supported in RemDev
    val monolithLayouter = MonolithUtils.findSessionById(id)?.debugProcess?.createTabLayouter()
    return monolithLayouter ?: object : XDebugTabLayouter() {} // TODO support additional tabs in RemDev
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
    // TODO: addittional actions are not registered in RemDev
    MonolithUtils.findSessionById(id)?.debugProcess?.registerAdditionalActions(leftToolbar, topLeftToolbar, settings)
  }

  override fun putKey(sink: DataSink) {
    // do nothing, proxy is already set in tab
  }

  override fun updateExecutionPosition() {
    cs.launch {
      XDebugSessionApi.getInstance().updateExecutionPosition(id)
    }
  }

  private fun onTabInitialized(tab: XDebugSessionTab) {
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

  override suspend fun stepOver(ignoreBreakpoints: Boolean) {
    XDebugSessionApi.getInstance().stepOver(id, ignoreBreakpoints)
  }

  override suspend fun stepOut() {
    XDebugSessionApi.getInstance().stepOut(id)
  }

  override suspend fun stepInto(ignoreBreakpoints: Boolean) {
    if (ignoreBreakpoints) {
      XDebugSessionApi.getInstance().forceStepInto(id)
    }
    else {
      XDebugSessionApi.getInstance().stepInto(id)
    }
  }

  override suspend fun runToPosition(position: XSourcePosition, ignoreBreakpoints: Boolean) {
    XDebugSessionApi.getInstance().runToPosition(id, position.toRpc(), ignoreBreakpoints)
  }

  override suspend fun pause() {
    XDebugSessionApi.getInstance().pause(id)
  }

  override suspend fun resume() {
    XDebugSessionApi.getInstance().resume(id)
  }

  override suspend fun switchToTopFrame() {
    XDebugSessionApi.getInstance().switchToTopFrame(id)
  }

  companion object {

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

/**
 * Note that data added to [com.intellij.openapi.util.UserDataHolder] is not synced with backend.
 */
private class FrontendXDebugSessionData(
  project: Project, sessionDataDto: XDebugSessionDataDto,
  cs: CoroutineScope,
  sessionStateFlow: StateFlow<XDebugSessionState>,
) : XDebugSessionData(project, sessionDataDto.configurationName) {
  private val id = sessionDataDto.id
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
        XDebugSessionApi.getInstance().muteBreakpoints(id, muted)
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

