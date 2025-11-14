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
import com.intellij.openapi.diagnostic.thisLogger
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
import com.intellij.xdebugger.impl.util.XDebugMonolithUtils
import com.intellij.xdebugger.ui.XDebugTabLayouter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Color
import java.util.concurrent.atomic.AtomicReference
import javax.swing.event.HyperlinkListener
import kotlin.time.Duration.Companion.seconds

private class StackFrameUpdate private constructor(val frame: FrontendXStackFrame?, val isFrameChanged: Boolean) {
  companion object {
    fun notifyChanged(frame: FrontendXStackFrame?): StackFrameUpdate = StackFrameUpdate(frame, true)
    fun noNotify(frame: FrontendXStackFrame?): StackFrameUpdate = StackFrameUpdate(frame, false)
  }
}

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

  @Volatile
  private var isTopFrameSelected = false

  private val eventsDispatcher = EventDispatcher.create(XDebugSessionListener::class.java)
  override val runContentDescriptorId: RunContentDescriptorIdImpl? = sessionDto.runContentDescriptorId
  override val id: XDebugSessionId = sessionDto.id

  @Volatile
  private var currenSourcePosition: XSourcePosition? = null

  @Volatile
  private var topSourcePosition: XSourcePosition? = null

  @Volatile
  private var currentExecutionStack: FrontendXExecutionStack? = null

  private val sessionStateFlow = MutableStateFlow(sessionDto.initialSessionState)
  private val suspendContext = AtomicReference<FrontendXSuspendContext?>(null)
  private val currentStackFrame = MutableStateFlow(StackFrameUpdate.noNotify(null))
  private val activeNonLineBreakpoint: StateFlow<XBreakpointProxy?> = channelFlow {
    sessionDto.activeNonLineBreakpointIdFlow.toFlow().collectLatest { breakpointId ->
      if (breakpointId == null) {
        send(null)
        return@collectLatest
      }
      val breakpoint = FrontendXDebuggerManager.getInstance(project).breakpointsManager.awaitBreakpointCreation(breakpointId)
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
    get() = getCurrentSuspendContext()?.lifetimeScope

  override val activeNonLineBreakpointFlow: Flow<XBreakpointProxy?>
    get() = activeNonLineBreakpoint

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

  private fun XDebuggerSessionEvent.updateCurrents() {
    when (this) {
      is XDebuggerSessionEvent.SessionPaused -> {
        updateState()
        suspendData?.applyToCurrents()
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
        isTopFrameSelected = isTopFrame
        currenSourcePosition = sourcePositionDto?.sourcePosition()
        topSourcePosition = topSourcePositionDto?.sourcePosition()
        val newFrame = stackFrame?.let {
          getCurrentSuspendContext()?.getOrCreateStackFrame(it)
        }
        currentStackFrame.value = StackFrameUpdate.notifyChanged(newFrame)
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
    isTopFrameSelected = false
    currenSourcePosition = null
    topSourcePosition = null
    currentExecutionStack = null
    currentStackFrame.value = StackFrameUpdate.noNotify(null)
  }

  private fun SuspendData.applyToCurrents() {
    val (suspendContextDto, executionStackDto, stackFrameDto, sourcePositionDto, topSourcePositionDto) = this
    val currentSuspendContext = getOrCreateSuspendContext(suspendContextDto)
    val suspendContextLifetimeScope = currentSuspendContext.lifetimeScope
    currenSourcePosition = sourcePositionDto?.sourcePosition()
    topSourcePosition = topSourcePositionDto?.sourcePosition()

    val stack = executionStackDto?.let {
      FrontendXExecutionStack(executionStackDto, project, suspendContextLifetimeScope)
    }
    currentExecutionStack = stack
    currentSuspendContext.activeExecutionStack = stack
    isTopFrameSelected = stack != null

    val frame = stackFrameDto?.let {
      suspendContextLifetimeScope.getOrCreateStackFrame(it, project)
    }
    currentStackFrame.value = StackFrameUpdate.noNotify(frame)
  }

  private fun getOrCreateSuspendContext(suspendContextDto: XSuspendContextDto): FrontendXSuspendContext {
    val oldSuspendContext = getCurrentSuspendContext()
    if (oldSuspendContext?.id == suspendContextDto.id) return oldSuspendContext

    val newSuspendContext = FrontendXSuspendContext(suspendContextDto, project, cs)
    val previousSuspendContext = suspendContext.getAndUpdate { newSuspendContext }
    previousSuspendContext?.cancel()
    return newSuspendContext
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
  private suspend fun initTabInfo(tabDto: XDebuggerSessionTabDto) {
    val (tabInfo, pausedFlow) = tabDto
    if (tabInfo !is XDebuggerSessionTabInfo) return
    val backendRunContentDescriptorId = tabInfo.backendRunContendDescriptorId.await()
    val executionEnvironmentId = tabInfo.executionEnvironmentId

    suspend fun onTabClosed() {
      try {
        tabInfo.tabClosedCallback.send(Unit)
      }
      catch (_: ClosedSendChannelException) {
        // closed on the backend
      }
      finally {
        tabScope.cancel()
      }
    }

    val proxy = this@FrontendXDebuggerSession
    val tab = withContext(Dispatchers.EDT) {
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
      }
    }

    tabScope.launch(Dispatchers.EDT) {
      tabInfo.showTab.await()
      tab.showTab()
    }

    val runContentDescriptor = tab.runContentDescriptor
    if (runContentDescriptor == null) {
      onTabClosed()
      thisLogger().error("Run content descriptor is not set for tab")
      return
    }
    runContentDescriptor.coroutineScope.awaitCancellationAndInvoke {
      onTabClosed()
    }
    // don't subscribe on additional tabs if we have [ExecutionEnvironment] (it means this is Monolith)
    if (tabInfo.executionEnvironmentProxyDto?.executionEnvironment == null) {
      subscribeOnAdditionalTabs(tabScope, tab, tabInfo.additionalTabsComponentManagerId)
    }

    tabScope.launch(Dispatchers.EDT) {
      pausedFlow.toFlow().collectLatest { paused ->
        tab.onPause(paused.pausedByUser, paused.topFrameIsAbsent)
      }
    }
  }

  override fun getCurrentPosition(): XSourcePosition? = currenSourcePosition

  override fun getTopFramePosition(): XSourcePosition? = topSourcePosition

  override fun getFrameSourcePosition(frame: XStackFrame): XSourcePosition? {
    // TODO Support XSourceKind
    return frame.sourcePosition
  }

  override fun getFrameSourcePosition(frame: XStackFrame, sourceKind: XSourceKind): XSourcePosition? {
    // TODO Support XSourceKind, need to adapt XAlternativeSourceHandler for the front-end use only
    if (sourceKind == XSourceKind.ALTERNATIVE) return null
    return frame.sourcePosition
  }

  override fun getCurrentExecutionStack(): XExecutionStack? = currentExecutionStack

  override fun getCurrentStackFrame(): XStackFrame? {
    return currentStackFrame.value.frame
  }

  override fun setCurrentStackFrame(executionStack: XExecutionStack, frame: XStackFrame, isTopFrame: Boolean) {
    frame as FrontendXStackFrame
    executionStack as FrontendXExecutionStack
    isTopFrameSelected = isTopFrame
    currentExecutionStack = executionStack

    // TODO Support XSourceKind
    val frameSourcePosition = frame.sourcePosition
    currenSourcePosition = frameSourcePosition
    if (isTopFrame) {
      topSourcePosition = frameSourcePosition
    }

    currentStackFrame.value = StackFrameUpdate.notifyChanged(frame)
    cs.launch {
      XDebugSessionApi.getInstance().setCurrentStackFrame(id, executionStack.id,
                                                          frame.id, isTopFrame, changedByUser = true)
    }
  }

  override fun isTopFrameSelected(): Boolean {
    return getCurrentStackFrame() != null && isTopFrameSelected
  }

  override fun hasSuspendContext(): Boolean {
    return getCurrentSuspendContext() != null
  }

  override fun isSteppingSuspendContext(): Boolean {
    val currentContext = getCurrentSuspendContext() ?: return false
    return currentContext.isStepping
  }

  override fun computeExecutionStacks(provideContainer: () -> XSuspendContext.XExecutionStackContainer) {
    getCurrentSuspendContext()?.computeExecutionStacks(provideContainer())
  }

  private fun getCurrentSuspendContext() = suspendContext.get()

  override fun createTabLayouter(): XDebugTabLayouter {
    // Additional tabs are not supported in RemDev
    val monolithLayouter = XDebugMonolithUtils.findSessionById(id)?.debugProcess?.createTabLayouter()
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
    XDebugMonolithUtils.findSessionById(id)?.debugProcess?.registerAdditionalActions(leftToolbar, topLeftToolbar, settings)
  }

  override fun putKey(sink: DataSink) {
    // do nothing, proxy is already set in tab
  }

  private fun onTabInitialized(tab: XDebugSessionTab) {
    cs.launch {
      XDebugSessionTabApi.getInstance().onTabInitialized(id, XDebuggerSessionTabInfoCallback(tab))
    }
  }

  override fun createFileColorsCache(framesList: XDebuggerFramesList): XStackFramesListColorsCache {
    return object : XStackFramesListColorsCache(project) {
      override fun get(stackFrame: XStackFrame): Color? {
        require(stackFrame is FrontendXStackFrame) { "Expected FrontendXStackFrame, got ${stackFrame::class.java}" }

        return stackFrame.backgroundColor
      }
    }
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

