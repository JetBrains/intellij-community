// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.rpc.toDto
import com.intellij.execution.runners.BackendExecutionEnvironmentProxy
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.ui.icons.rpcId
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive
import com.intellij.ui.AppUIUtil.invokeOnEdt
import com.intellij.util.EventDispatcher
import com.intellij.util.SmartList
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xdebugger.*
import com.intellij.xdebugger.breakpoints.*
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XDebuggerPerformanceCollector.logBreakpointReached
import com.intellij.xdebugger.impl.XDebuggerSuspendScopeProvider.provideSuspendScope
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.breakpoints.BreakpointsUsageCollector.reportBreakpointVerified
import com.intellij.xdebugger.impl.breakpoints.CustomizedBreakpointPresentation
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil.getShortText
import com.intellij.xdebugger.impl.breakpoints.XDependentBreakpointListener
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl
import com.intellij.xdebugger.impl.evaluate.ValueLookupManagerController
import com.intellij.xdebugger.impl.frame.FileColorsComputer
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy.Companion.showFeWarnings
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy.Companion.useFeLineBreakpointProxy
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy.Companion.useFeProxy
import com.intellij.xdebugger.impl.frame.XDebugSessionProxyKeeper
import com.intellij.xdebugger.impl.frame.XValueMarkers
import com.intellij.xdebugger.impl.frame.asProxy
import com.intellij.xdebugger.impl.inline.DebuggerInlayListener
import com.intellij.xdebugger.impl.inline.InlineDebugRenderer
import com.intellij.xdebugger.impl.mixedmode.XMixedModeCombinedDebugProcess
import com.intellij.xdebugger.impl.rpc.*
import com.intellij.xdebugger.impl.rpc.models.XDebugSessionValueIdType
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl
import com.intellij.xdebugger.impl.ui.XDebugSessionData
import com.intellij.xdebugger.impl.ui.XDebugSessionTab
import com.intellij.xdebugger.impl.ui.allowFramesViewCustomization
import com.intellij.xdebugger.impl.ui.forceShowNewDebuggerUi
import com.intellij.xdebugger.impl.util.start
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.*
import java.util.function.Consumer
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import kotlin.concurrent.Volatile
import kotlin.coroutines.EmptyCoroutineContext

@ApiStatus.Internal
class XDebugSessionImpl @JvmOverloads constructor(
  environment: ExecutionEnvironment?,
  private val debuggerManager: XDebuggerManagerImpl,
  sessionName: @Nls String = environment?.runProfile?.getName() ?: "",
  icon: Icon? = environment?.runProfile?.getIcon(),
  showToolWindowOnSuspendOnly: Boolean = false,
  contentToReuse: RunContentDescriptor? = null,
) : XDebugSession {
  @ApiStatus.Internal
  val coroutineScope: CoroutineScope = debuggerManager.coroutineScope.childScope("XDebugSession $sessionName", EmptyCoroutineContext, true)
  val id: XDebugSessionId = storeValueGlobally(coroutineScope, this, type = XDebugSessionValueIdType)

  private var myDebugProcess: XDebugProcess? = null
  private val myRegisteredBreakpoints: MutableMap<XBreakpoint<*>?, CustomizedBreakpointPresentation?> = HashMap<XBreakpoint<*>?, CustomizedBreakpointPresentation?>()
  private val myInactiveSlaveBreakpoints: MutableSet<XBreakpoint<*>?> = Collections.synchronizedSet<XBreakpoint<*>?>(
    HashSet<XBreakpoint<*>?>())
  private var myBreakpointsDisabled = false
  private val myDebuggerManager: XDebuggerManagerImpl = debuggerManager
  private val myExecutionPointManager: XDebuggerExecutionPointManager = debuggerManager.executionPointManager
  private var myBreakpointListenerDisposable: Disposable? = null

  @get:ApiStatus.Internal
  var currentSuspendCoroutineScope: CoroutineScope? = null
    private set
  private var myAlternativeSourceHandler: XAlternativeSourceHandler? = null
  private var myIsTopFrame = false

  private val myTopStackFrame = MutableStateFlow<XStackFrame?>(null)
  private val myPaused = MutableStateFlow<Boolean>(false)
  private var myValueMarkers: XValueMarkers<*, *>? = null
  private val mySessionName: @Nls String = sessionName
  private var mySessionTab: XDebugSessionTab? = null
  private var myRunContentDescriptor: RunContentDescriptor? = null
  val sessionData: XDebugSessionData
  private val myActiveNonLineBreakpointAndPositionFlow = MutableStateFlow<Pair<XBreakpoint<*>, XSourcePosition?>?>(null)
  private val myPausedEvents = MutableSharedFlow<XDebugSessionPausedInfo>(extraBufferCapacity = 1)
  private val myDispatcher = EventDispatcher.create<XDebugSessionListener>(XDebugSessionListener::class.java)
  private val myProject: Project = debuggerManager.project

  val executionEnvironment: ExecutionEnvironment? = environment
  private val myStopped = MutableStateFlow<Boolean>(false)
  private val myReadOnly = MutableStateFlow<Boolean>(false)
  private val myShowToolWindowOnSuspendOnly: Boolean = showToolWindowOnSuspendOnly
  private val myTabInitDataFlow = createMutableStateFlow<XDebuggerSessionTabAbstractInfo?>(null)
  val restartActions: MutableList<AnAction> = SmartList<AnAction>()
  val extraStopActions: MutableList<AnAction> = SmartList<AnAction>()
  val extraActions: MutableList<AnAction> = SmartList<AnAction>()
  private var myConsoleView: ConsoleView? = null
  private val myIcon: Icon? = icon
  private val myCurrentStackFrameManager = XDebugSessionCurrentStackFrameManager()
  private val executionStackFlow = MutableStateFlow<Ref<XExecutionStack?>>(Ref.create(null))
  @get:ApiStatus.Internal
  val fileColorsComputer: FileColorsComputer = FileColorsComputer(project, coroutineScope)

  var currentExecutionStack: XExecutionStack?
    get() = executionStackFlow.value.get()
    private set(value) {
      if (executionStackFlow.value.get() === value) return
      executionStackFlow.value = Ref.create(value)
    }
  private val suspendContextFlow = MutableStateFlow<XSuspendContext?>(null)
  private val mySuspendContext: StateFlow<XSuspendContext?>
    get() = suspendContextFlow
  private val sessionInitializedDeferred = CompletableDeferred<Unit>()

  @get:ApiStatus.Internal
  val isSuspendedState: StateFlow<Boolean> = combine(myPaused, mySuspendContext) { paused, suspendContext ->
    paused && suspendContext != null
  }.stateIn(coroutineScope, SharingStarted.Eagerly, myPaused.value && mySuspendContext.value != null)

  @Volatile
  private var breakpointsInitialized = false
  private var myUserRequestStart: Long = 0
  private var myUserRequestAction: String? = null

  private val myActiveNonLineBreakpointFlow = myActiveNonLineBreakpointAndPositionFlow
    .combine(myTopStackFrame) { breakpointAndPosition, _ ->
      val (breakpoint, breakpointPosition) = breakpointAndPosition ?: return@combine null
      if (breakpointPosition == null) return@combine breakpoint
      val position = topFramePosition ?: return@combine null
      val samePosition = breakpointPosition.getFile() == position.getFile() && breakpointPosition.getLine() == position.getLine()
      breakpoint.takeIf { !samePosition }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, null)

  init {
    var contentToReuse = contentToReuse
    ValueLookupManagerController.getInstance(myProject).startListening()
    DebuggerInlayListener.getInstance(myProject).startListening()

    var oldSessionData: XDebugSessionData? = null
    if (contentToReuse == null) {
      contentToReuse = environment?.contentToReuse
    }
    if (contentToReuse != null) {
      val component = contentToReuse.component
      if (component != null) {
        oldSessionData = XDebugSessionData.DATA_KEY.getData(DataManager.getInstance().getDataContext(component))
      }
    }

    val currentConfigurationName = computeConfigurationName()
    if (oldSessionData == null || oldSessionData.configurationName != currentConfigurationName) {
      oldSessionData = XDebugSessionData(myProject, currentConfigurationName)
    }
    this.sessionData = oldSessionData
  }

  override fun getSessionName(): String {
    return mySessionName
  }

  @get:ApiStatus.Internal
  val tabInitDataFlow: Flow<XDebuggerSessionTabAbstractInfo?>
    get() = myTabInitDataFlow

  @get:ApiStatus.Internal
  val topFrameFlow: Flow<XStackFrame?>
    get() = myTopStackFrame

  override fun getRunContentDescriptor(): RunContentDescriptor {
    if (useFeProxy() && showFeWarnings()) {
      LOG.error("RunContentDescriptor should not be used in split mode from XDebugSession")
    }
    val descriptor = myRunContentDescriptor
    LOG.assertTrue(descriptor != null, "Run content descriptor is not initialized yet!")
    return descriptor!!
  }

  private val isTabInitialized: Boolean
    get() = myTabInitDataFlow.value != null && (useFeProxy() || mySessionTab != null)

  private fun assertSessionTabInitialized() {
    if (myShowToolWindowOnSuspendOnly && !this.isTabInitialized) {
      LOG.error("Debug tool window isn't shown yet because debug process isn't suspended")
    }
    else {
      LOG.assertTrue(this.isTabInitialized, "Debug tool window not initialized yet!")
    }
  }

  override fun setPauseActionSupported(isSupported: Boolean) {
    sessionData.isPauseSupported = isSupported
  }

  val isReadOnlyState: StateFlow<Boolean>
    get() = myReadOnly

  var isReadOnly: Boolean
    get() = myReadOnly.value
    set(readOnly) {
      myReadOnly.value = readOnly
    }

  fun addRestartActions(vararg restartActions: AnAction?) {
    Collections.addAll<AnAction?>(this.restartActions, *restartActions)
  }

  fun addExtraActions(vararg extraActions: AnAction?) {
    Collections.addAll<AnAction?>(this.extraActions, *extraActions)
  }

  // used externally
  @Suppress("unused")
  fun addExtraStopActions(vararg extraStopActions: AnAction?) {
    Collections.addAll<AnAction?>(this.extraStopActions, *extraStopActions)
  }

  override fun rebuildViews() {
    myDispatcher.getMulticaster().settingsChanged()
  }

  override fun getRunProfile(): RunProfile? {
    return if (this.executionEnvironment != null) executionEnvironment.runProfile else null
  }

  val isPauseActionSupportedState: StateFlow<Boolean>
    get() = sessionData.pauseSupportedFlow

  @JvmName("isPauseActionSupported")
  fun isPauseActionSupported(): Boolean {
    return sessionData.isPauseSupported
  }

  override fun getProject(): Project {
    return myDebuggerManager.project
  }

  override fun getDebugProcess(): XDebugProcess {
    return myDebugProcess!!
  }

  override fun isSuspended(): Boolean {
    return isSuspendedState.value
  }

  @get:ApiStatus.Internal
  val isPausedState: StateFlow<Boolean>
    get() = myPaused

  @ApiStatus.Internal
  fun getPausedEventsFlow(): Flow<XDebugSessionPausedInfo?> {
    return myPausedEvents
  }

  @ApiStatus.Internal
  fun getCurrentExecutionStackFlow(): Flow<XExecutionStack?> {
    return executionStackFlow.map { it.get() }
  }

  override fun isPaused(): Boolean {
    return myPaused.value
  }

  @ApiStatus.Internal
  fun sessionInitializedDeferred(): Deferred<Unit> {
    return sessionInitializedDeferred
  }

  override fun getCurrentStackFrame(): XStackFrame? {
    return myCurrentStackFrameManager.getCurrentStackFrame()
  }

  @ApiStatus.Internal
  fun getCurrentStackFrameFlow(): Flow<XStackFrame?> {
    return myCurrentStackFrameManager.getCurrentStackFrameFlow().map { it.get() }
  }

  override fun getSuspendContext(): XSuspendContext? {
    return mySuspendContext.value
  }

  @ApiStatus.Internal
  fun getCurrentSuspendContextFlow(): Flow<XSuspendContext?> {
    return mySuspendContext
  }

  override fun getCurrentPosition(): XSourcePosition? {
    return getFrameSourcePosition(currentStackFrame)
  }

  @ApiStatus.Internal
  fun getCurrentPositionFlow(): Flow<XSourcePosition?> {
    return getCurrentStackFrameFlow().map {
      getFrameSourcePosition(it)
    }
  }

  override fun getTopFramePosition(): XSourcePosition? {
    return getFrameSourcePosition(myTopStackFrame.value)
  }

  fun getFrameSourcePosition(frame: XStackFrame?): XSourcePosition? {
    return getFrameSourcePosition(frame, this.currentSourceKind)
  }

  fun getFrameSourcePosition(frame: XStackFrame?, sourceKind: XSourceKind): XSourcePosition? {
    if (frame == null) return null
    return when (sourceKind) {
      XSourceKind.MAIN -> frame.sourcePosition
      XSourceKind.ALTERNATIVE -> if (myAlternativeSourceHandler != null) myAlternativeSourceHandler!!.getAlternativePosition(frame)
      else null
    }
  }

  val currentSourceKind: XSourceKind
    get() {
      val state = this.alternativeSourceKindState
      return if (state.value) XSourceKind.ALTERNATIVE else XSourceKind.MAIN
    }

  val alternativeSourceKindState: StateFlow<Boolean>
    get() = if (myAlternativeSourceHandler != null) myAlternativeSourceHandler!!.getAlternativeSourceKindState() else ALWAYS_FALSE_STATE

  fun init(process: XDebugProcess, contentToReuse: RunContentDescriptor?) {
    LOG.assertTrue(myDebugProcess == null)
    myDebugProcess = process
    myAlternativeSourceHandler = process.alternativeSourceHandler
    myExecutionPointManager.alternativeSourceKindFlow = this.alternativeSourceKindState

    if (process.checkCanInitBreakpoints()) {
      ReadAction.run<RuntimeException?>(ThrowableRunnable { initBreakpoints() })
    }
    if (process is XDebugProcessDebuggeeInForeground &&
        process.isBringingToForegroundApplicable()
    ) {
      process.start(this, 1000)
    }

    process.getProcessHandler().addProcessListener(object : ProcessListener {
      override fun processTerminated(event: ProcessEvent) {
        stopImpl()
        process.getProcessHandler().removeProcessListener(this)
      }
    })
    //todo make 'createConsole()' method return ConsoleView
    myConsoleView = process.createConsole() as ConsoleView
    if (!myShowToolWindowOnSuspendOnly) {
      initSessionTab(contentToReuse, false)
    }
    sessionInitializedDeferred.complete(Unit)
  }

  fun reset() {
    breakpointsInitialized = false
    removeBreakpointListeners()
    unsetPaused()
    clearPausedData()
    rebuildViews()
  }

  @RequiresReadLock
  override fun initBreakpoints() {
    LOG.assertTrue(!breakpointsInitialized)
    breakpointsInitialized = true

    disableSlaveBreakpoints()
    processAllBreakpoints(true, false)

    if (myBreakpointListenerDisposable == null) {
      myBreakpointListenerDisposable = Disposer.newDisposable()
      Disposer.register(myProject, myBreakpointListenerDisposable!!)
      val busConnection = myProject.getMessageBus().connect(myBreakpointListenerDisposable!!)
      busConnection.subscribe<XBreakpointListener<*>>(XBreakpointListener.TOPIC, MyBreakpointListener())
      busConnection.subscribe<XDependentBreakpointListener>(XDependentBreakpointListener.TOPIC, MyDependentBreakpointListener())
    }
  }

  override fun getConsoleView(): ConsoleView? {
    return myConsoleView
  }

  val sessionTab: XDebugSessionTab?
    get() {
      if (useFeProxy() && showFeWarnings()) {
        LOG.error("Debug tab should not be used in split mode from XDebugSession")
      }
      return mySessionTab
    }

  override fun getUI(): RunnerLayoutUi {
    assertSessionTabInitialized()
    val sessionTab: XDebugSessionTab? = checkNotNull(this.sessionTab)
    return sessionTab!!.ui
  }

  override fun isMixedMode(): Boolean {
    return myDebugProcess is XMixedModeCombinedDebugProcess
  }

  private fun initSessionTab(contentToReuse: RunContentDescriptor?, shouldShowTab: Boolean) {
    val forceNewDebuggerUi = debugProcess.forceShowNewDebuggerUi()
    val withFramesCustomization = debugProcess.allowFramesViewCustomization()

    if (useFeProxy()) {
      val environmentCoroutineScope = debuggerManager.coroutineScope.childScope("ExecutionEnvironmentDto")
      val tabClosedChannel = Channel<Unit>(capacity = 1)
      val tabInfo = XDebuggerSessionTabInfo(myIcon?.rpcId(), forceNewDebuggerUi, withFramesCustomization,
                                            contentToReuse, executionEnvironment?.toDto(environmentCoroutineScope), tabClosedChannel)
      if (myTabInitDataFlow.compareAndSet(null, tabInfo)) {
        // TODO: take contentToReuse into account
        // This is a mock descriptor used in backend only
        val mockDescriptor = object : RunContentDescriptor(myConsoleView, debugProcess.getProcessHandler(), JLabel(),
                                                           sessionName, myIcon, null) {
          override fun isHiddenContent(): Boolean = true
        }
        debuggerManager.coroutineScope.launch(Dispatchers.EDT) {
          for (tabClosedRequest in tabClosedChannel) {
            environmentCoroutineScope.cancel()
            Disposer.dispose(mockDescriptor)
          }
        }
        myRunContentDescriptor = mockDescriptor
        myDebugProcess!!.sessionInitialized()
      }
      else {
        environmentCoroutineScope.cancel()
        tabClosedChannel.close()
      }
    }
    else {
      if (myTabInitDataFlow.value != null) return
      val proxy = this.asProxy()
      val tab = XDebugSessionTab.create(proxy, myIcon, executionEnvironment?.let { BackendExecutionEnvironmentProxy(it) }, contentToReuse,
                                        forceNewDebuggerUi, withFramesCustomization)
      if (myTabInitDataFlow.compareAndSet(null, XDebuggerSessionTabInfoNoInit(tab))) {
        tabInitialized(tab)
        myDebugProcess!!.sessionInitialized()
        if (shouldShowTab) {
          tab.showTab()
        }
      }
    }
  }

  @ApiStatus.Internal
  fun tabInitialized(sessionTab: XDebugSessionTab) {
    mySessionTab = sessionTab
    myRunContentDescriptor = sessionTab.runContentDescriptor
  }

  private fun disableSlaveBreakpoints() {
    val slaveBreakpoints = myDebuggerManager.breakpointManager.dependentBreakpointManager.allSlaveBreakpoints
    if (slaveBreakpoints.isEmpty()) {
      return
    }

    val breakpointTypes: MutableSet<XBreakpointType<*, *>?> = HashSet<XBreakpointType<*, *>?>()
    for (handler in myDebugProcess!!.breakpointHandlers) {
      breakpointTypes.add(getBreakpointTypeClass(handler))
    }
    for (slaveBreakpoint in slaveBreakpoints) {
      if (breakpointTypes.contains(slaveBreakpoint.getType())) {
        myInactiveSlaveBreakpoints.add(slaveBreakpoint)
      }
    }
  }

  fun showSessionTab() {
    val tab = checkNotNull(this.sessionTab)
    tab.showTab()
  }

  val valueMarkers: XValueMarkers<*, *>?
    @JvmName("valueMarkers")
    get() {
      if (myValueMarkers == null) {
        val provider = myDebugProcess!!.createValueMarkerProvider()
        if (provider != null) {
          myValueMarkers = XValueMarkers.createValueMarkers(provider)
        }
      }
      return myValueMarkers
    }

  @JvmName("getValueMarkers")
  fun getValueMarkers(): XValueMarkers<*, *>? {
    return valueMarkers
  }

  private fun <B : XBreakpoint<*>?> processBreakpoints(
    handler: XBreakpointHandler<*>,
    register: Boolean,
    temporary: Boolean,
  ) {
    @Suppress("UNCHECKED_CAST")
    handler as XBreakpointHandler<B?>
    val breakpoints = myDebuggerManager.breakpointManager.getBreakpoints<B?>(handler.getBreakpointTypeClass())
    for (b in breakpoints) {
      handleBreakpoint<B?>(handler, b, register, temporary)
    }
  }

  private fun <B : XBreakpoint<*>?> handleBreakpoint(
    handler: XBreakpointHandler<B?>, b: B?, register: Boolean,
    temporary: Boolean,
  ) {
    if (register) {
      val active = ReadAction.compute<Boolean, RuntimeException?>(ThrowableComputable { isBreakpointActive(b!!) })
      if (active) {
        synchronized(myRegisteredBreakpoints) {
          myRegisteredBreakpoints.put(b, CustomizedBreakpointPresentation())
          if (b is XLineBreakpoint<*>) {
            updateBreakpointPresentation(b as XLineBreakpoint<*>, b.getType().pendingIcon, null)
          }
        }
        handler.registerBreakpoint(b!!)
      }
    }
    else {
      val removed: Boolean
      synchronized(myRegisteredBreakpoints) {
        removed = myRegisteredBreakpoints.remove(b) != null
      }
      if (removed) {
        handler.unregisterBreakpoint(b!!, temporary)
      }
    }
  }

  fun getBreakpointPresentation(breakpoint: XBreakpoint<*>): CustomizedBreakpointPresentation? {
    synchronized(myRegisteredBreakpoints) {
      return myRegisteredBreakpoints[breakpoint]
    }
  }

  private fun processAllHandlers(breakpoint: XBreakpoint<*>, register: Boolean) {
    for (handler in myDebugProcess!!.breakpointHandlers) {
      processBreakpoint(breakpoint, handler, register)
    }
  }

  private fun <B : XBreakpoint<*>> processBreakpoint(
    breakpoint: B,
    handler: XBreakpointHandler<*>,
    register: Boolean,
  ) {
    val type = breakpoint.getType()
    if (handler.getBreakpointTypeClass() == type.javaClass) {
      @Suppress("UNCHECKED_CAST")
      handleBreakpoint(handler as XBreakpointHandler<B?>, breakpoint, register, false)
    }
  }

  @RequiresReadLock
  fun isBreakpointActive(b: XBreakpoint<*>): Boolean {
    return !areBreakpointsMuted() && b.isEnabled() && !isInactiveSlaveBreakpoint(b) && !(b as XBreakpointBase<*, *, *>).isDisposed
  }

  override fun areBreakpointsMuted(): Boolean {
    return sessionData.isBreakpointsMuted
  }
  
  @ApiStatus.Internal
  fun getBreakpointsMutedFlow(): StateFlow<Boolean> {
    return sessionData.breakpointsMutedFlow
  }

  override fun addSessionListener(listener: XDebugSessionListener, parentDisposable: Disposable) {
    myDispatcher.addListener(listener, parentDisposable)
  }

  override fun addSessionListener(listener: XDebugSessionListener) {
    myDispatcher.addListener(listener)
  }

  override fun removeSessionListener(listener: XDebugSessionListener) {
    myDispatcher.removeListener(listener)
  }

  @RequiresReadLock
  override fun setBreakpointMuted(muted: Boolean) {
    if (areBreakpointsMuted() == muted) return
    sessionData.isBreakpointsMuted = muted
    if (!myBreakpointsDisabled) {
      processAllBreakpoints(!muted, muted)
    }
    myDebuggerManager.breakpointManager.lineBreakpointManager.queueAllBreakpointsUpdate()
    myDispatcher.getMulticaster().breakpointsMuted(muted)
  }

  override fun stepOver(ignoreBreakpoints: Boolean) {
    rememberUserActionStart(XDebuggerActions.STEP_OVER)
    if (!myDebugProcess!!.checkCanPerformCommands()) return

    if (ignoreBreakpoints) {
      setBreakpointsDisabledTemporarily(true)
    }
    myDebugProcess!!.startStepOver(doResume())
  }

  override fun stepInto() {
    rememberUserActionStart(XDebuggerActions.STEP_INTO)
    if (!myDebugProcess!!.checkCanPerformCommands()) return

    myDebugProcess!!.startStepInto(doResume())
  }

  override fun stepOut() {
    rememberUserActionStart(XDebuggerActions.STEP_OUT)
    if (!myDebugProcess!!.checkCanPerformCommands()) return

    myDebugProcess!!.startStepOut(doResume())
  }

  override fun <V : XSmartStepIntoVariant?> smartStepInto(handler: XSmartStepIntoHandler<V?>, variant: V?) {
    rememberUserActionStart(XDebuggerActions.SMART_STEP_INTO)
    if (!myDebugProcess!!.checkCanPerformCommands()) return

    val context = doResume()
    handler.startStepInto(variant!!, context)
  }

  override fun forceStepInto() {
    rememberUserActionStart(XDebuggerActions.FORCE_STEP_INTO)
    if (!myDebugProcess!!.checkCanPerformCommands()) return

    myDebugProcess!!.startForceStepInto(doResume())
  }

  override fun runToPosition(position: XSourcePosition, ignoreBreakpoints: Boolean) {
    rememberUserActionStart(XDebuggerActions.RUN_TO_CURSOR)
    if (!myDebugProcess!!.checkCanPerformCommands()) return

    if (ignoreBreakpoints) {
      setBreakpointsDisabledTemporarily(true)
    }
    myDebugProcess!!.runToPosition(position, doResume())
  }

  override fun pause() {
    rememberUserActionStart(XDebuggerActions.PAUSE)
    if (!myDebugProcess!!.checkCanPerformCommands()) return

    myDebugProcess!!.startPausing()
  }

  @RequiresReadLock
  private fun processAllBreakpoints(register: Boolean, temporary: Boolean) {
    for (handler in myDebugProcess!!.breakpointHandlers) {
      processBreakpoints<XBreakpoint<*>?>(handler, register, temporary)
    }
  }

  private fun setBreakpointsDisabledTemporarily(disabled: Boolean) {
    ApplicationManager.getApplication().runReadAction(Runnable {
      if (myBreakpointsDisabled == disabled) return@Runnable
      myBreakpointsDisabled = disabled
      if (!areBreakpointsMuted()) {
        processAllBreakpoints(!disabled, disabled)
      }
    })
  }

  override fun resume() {
    if (!myDebugProcess!!.checkCanPerformCommands()) return

    myDebugProcess!!.resume(doResume())
  }

  private fun doResume(): XSuspendContext? {
    if (!myPaused.getAndUpdate { false }) {
      return null
    }

    myDispatcher.getMulticaster().beforeSessionResume()
    val context = mySuspendContext.value
    clearPausedData()
    myDispatcher.getMulticaster().sessionResumed()
    return context
  }

  private fun clearPausedData() {
    // If the scope is not provided by an XSuspendContent implementation,
    // then a default scope, provided by XDebuggerSuspendScopeProvider is used,
    // and it must be canceled manually
    if (mySuspendContext.value?.coroutineScope != null) {
      currentSuspendCoroutineScope?.cancel()
    }
    currentSuspendCoroutineScope = null
    suspendContextFlow.value = null
    this.currentExecutionStack = null
    myCurrentStackFrameManager.setCurrentStackFrame(null)
    myTopStackFrame.value = null
    clearActiveNonLineBreakpoint()
    updateExecutionPosition()
  }

  override fun updateExecutionPosition() {
    updateExecutionPosition(this.currentSourceKind)
  }

  private fun updateExecutionPosition(navigationSourceKind: XSourceKind) {
    // allowed only for the active session
    if (myDebuggerManager.currentSession == this) {
      val isTopFrame = this.isTopFrameSelected
      val mainSourcePosition = getFrameSourcePosition(currentStackFrame, XSourceKind.MAIN)
      val alternativeSourcePosition = getFrameSourcePosition(currentStackFrame, XSourceKind.ALTERNATIVE)
      myExecutionPointManager.setExecutionPoint(mainSourcePosition, alternativeSourcePosition, isTopFrame, navigationSourceKind)
      updateExecutionPointGutterIconRenderer()
    }
  }

  val isTopFrameSelected: Boolean
    get() = this.currentExecutionStack != null && myIsTopFrame


  override fun showExecutionPoint() {
    if (mySuspendContext.value != null) {
      val executionStack = mySuspendContext.value!!.activeExecutionStack
      if (executionStack != null) {
        val topFrame = executionStack.getTopFrame()
        if (topFrame != null) {
          setCurrentStackFrame(executionStack, topFrame, true)
          myExecutionPointManager.showExecutionPosition()
        }
      }
    }
  }

  override fun setCurrentStackFrame(executionStack: XExecutionStack, frame: XStackFrame, isTopFrame: Boolean) {
    if (mySuspendContext.value == null) return

    val frameChanged = currentStackFrame !== frame
    this.currentExecutionStack = executionStack
    myCurrentStackFrameManager.setCurrentStackFrame(frame)
    myIsTopFrame = isTopFrame

    if (frameChanged) {
      myDispatcher.getMulticaster().stackFrameChanged()
    }

    activateSession(frameChanged)
  }

  fun activateSession(forceUpdateExecutionPosition: Boolean) {
    val sessionChanged = myDebuggerManager.setCurrentSession(this)
    if (sessionChanged || forceUpdateExecutionPosition) {
      updateExecutionPosition()
    }
    else {
      myExecutionPointManager.showExecutionPosition()
    }
  }

  val activeNonLineBreakpoint: XBreakpoint<*>? get() = myActiveNonLineBreakpointFlow.value
  val activeNonLineBreakpointFlow: StateFlow<XBreakpoint<*>?> get() = myActiveNonLineBreakpointFlow

  fun checkActiveNonLineBreakpointOnRemoval(removedBreakpoint: XBreakpoint<*>) {
    val (breakpoint, _) = myActiveNonLineBreakpointAndPositionFlow.value ?: return
    if (breakpoint === removedBreakpoint) {
      clearActiveNonLineBreakpoint()
      updateExecutionPointGutterIconRenderer()
    }
  }

  private fun clearActiveNonLineBreakpoint() {
    myActiveNonLineBreakpointAndPositionFlow.value = null
  }

  fun updateExecutionPointGutterIconRenderer() {
    if (myDebuggerManager.currentSession == this) {
      val isTopFrame = this.isTopFrameSelected
      val renderer = getPositionIconRenderer(isTopFrame)
      myExecutionPointManager.gutterIconRenderer = renderer
    }
  }

  private fun getPositionIconRenderer(isTopFrame: Boolean): GutterIconRenderer? {
    if (!isTopFrame) {
      return null
    }
    val activeNonLineBreakpoint = this.activeNonLineBreakpoint
    if (activeNonLineBreakpoint != null) {
      return (activeNonLineBreakpoint as XBreakpointBase<*, *, *>).createGutterIconRenderer()
    }
    return currentExecutionStack?.executionLineIconRenderer
  }

  override fun updateBreakpointPresentation(
    breakpoint: XLineBreakpoint<*>,
    icon: Icon?,
    errorMessage: String?,
  ) {
    val presentation: CustomizedBreakpointPresentation?
    synchronized(myRegisteredBreakpoints) {
      presentation = myRegisteredBreakpoints[breakpoint]
      if (presentation == null ||
          (Comparing.equal<Icon?>(presentation.icon, icon) && Comparing.strEqual(presentation.errorMessage, errorMessage))
      ) {
        return
      }

      presentation.errorMessage = errorMessage
      presentation.icon = icon

      val timestamp = presentation.timestamp
      if (timestamp != 0L && XDebuggerUtilImpl.getVerifiedIcon(breakpoint) == icon) {
        val delay = System.currentTimeMillis() - timestamp
        presentation.timestamp = 0
        reportBreakpointVerified(breakpoint, delay)
      }
    }
    val debuggerManager = myDebuggerManager.breakpointManager
    if (useFeLineBreakpointProxy() && breakpoint is XLineBreakpointImpl<*>) {
      // for useFeLineBreakpointProxy we call update directly since visual presentation is disabled on the backend
      breakpoint.fireBreakpointPresentationUpdated(this)
    }
    else {
      debuggerManager.lineBreakpointManager.queueBreakpointUpdate(breakpoint, Runnable {
        (breakpoint as XBreakpointBase<*, *, *>).fireBreakpointPresentationUpdated(this)
      })
    }
  }

  override fun setBreakpointVerified(breakpoint: XLineBreakpoint<*>) {
    updateBreakpointPresentation(breakpoint, XDebuggerUtilImpl.getVerifiedIcon(breakpoint), null)
  }

  override fun setBreakpointInvalid(breakpoint: XLineBreakpoint<*>, errorMessage: String?) {
    updateBreakpointPresentation(breakpoint, AllIcons.Debugger.Db_invalid_breakpoint, errorMessage)
  }

  override fun breakpointReached(
    breakpoint: XBreakpoint<*>, evaluatedLogExpression: String?,
    suspendContext: XSuspendContext,
  ): Boolean {
    return breakpointReached(breakpoint, evaluatedLogExpression, suspendContext, true)
  }

  @ApiStatus.Internal
  fun breakpointReachedNoProcessing(breakpoint: XBreakpoint<*>, suspendContext: XSuspendContext) {
    breakpointReached(breakpoint, null, suspendContext, false)
  }

  private fun breakpointReached(
    breakpoint: XBreakpoint<*>, evaluatedLogExpression: String?,
    suspendContext: XSuspendContext, doProcessing: Boolean,
  ): Boolean {
    if (doProcessing) {
      if (breakpoint.isLogMessage()) {
        val position = breakpoint.getSourcePosition()
        val hyperlinkInfo =
          if (position != null) OpenFileHyperlinkInfo(myProject, position.getFile(), position.getLine()) else null
        printMessage(XDebuggerBundle.message("xbreakpoint.reached.text") + " ", getShortText(breakpoint), hyperlinkInfo)
      }

      if (breakpoint.isLogStack()) {
        myDebugProcess!!.logStack(suspendContext, this)
      }

      if (evaluatedLogExpression != null) {
        printMessage(evaluatedLogExpression, null, null)
      }

      processDependencies(breakpoint)

      if (breakpoint.getSuspendPolicy() == SuspendPolicy.NONE) {
        return false
      }
    }

    NotificationGroupManager.getInstance().getNotificationGroup("Breakpoint hit")
      .createNotification(XDebuggerBundle.message("xdebugger.breakpoint.reached"), MessageType.INFO)
      .notify(project)

    if (breakpoint !is XLineBreakpoint<*> || breakpoint.getType().canBeHitInOtherPlaces()) {
      // precompute source position for faster access later
      myActiveNonLineBreakpointAndPositionFlow.value = breakpoint to breakpoint.getSourcePosition()
    }
    else {
      myActiveNonLineBreakpointAndPositionFlow.value = null
    }

    // set this session active on breakpoint, update execution position will be called inside positionReached
    myDebuggerManager.setCurrentSession(this)

    positionReachedInternal(suspendContext, true)

    if (doProcessing && breakpoint is XLineBreakpoint<*> && breakpoint.isTemporary()) {
      handleTemporaryBreakpointHit(breakpoint)
    }
    return true
  }

  private fun handleTemporaryBreakpointHit(breakpoint: XBreakpoint<*>?) {
    addSessionListener(object : XDebugSessionListener {
      fun removeBreakpoint() {
        XDebuggerUtil.getInstance().removeBreakpoint(myProject, breakpoint)
        removeSessionListener(this)
      }

      override fun sessionResumed() {
        removeBreakpoint()
      }

      override fun sessionStopped() {
        removeBreakpoint()
      }
    })
  }

  fun processDependencies(breakpoint: XBreakpoint<*>) {
    val dependentBreakpointManager = myDebuggerManager.breakpointManager.dependentBreakpointManager
    if (!dependentBreakpointManager.isMasterOrSlave(breakpoint)) return

    val breakpoints = dependentBreakpointManager.getSlaveBreakpoints(breakpoint)
    breakpoints.forEach(Consumer { o: XBreakpoint<*>? -> myInactiveSlaveBreakpoints.remove(o) })
    for (slaveBreakpoint in breakpoints) {
      processAllHandlers(slaveBreakpoint, true)
    }

    if (dependentBreakpointManager.getMasterBreakpoint(breakpoint) != null && !dependentBreakpointManager.isLeaveEnabled(breakpoint)) {
      val added = myInactiveSlaveBreakpoints.add(breakpoint)
      if (added) {
        processAllHandlers(breakpoint, false)
        myDebuggerManager.breakpointManager.lineBreakpointManager.queueBreakpointUpdate(breakpoint)
      }
    }
  }

  private fun printMessage(message: String, hyperLinkText: String?, info: HyperlinkInfo?) {
    invokeOnEdt(Runnable {
      myConsoleView!!.print(message, ConsoleViewContentType.SYSTEM_OUTPUT)
      if (info != null) {
        myConsoleView!!.printHyperlink(hyperLinkText!!, info)
      }
      else if (hyperLinkText != null) {
        myConsoleView!!.print(hyperLinkText, ConsoleViewContentType.SYSTEM_OUTPUT)
      }
      myConsoleView!!.print("\n", ConsoleViewContentType.SYSTEM_OUTPUT)
    })
  }

  fun unsetPaused() {
    myPaused.value = false
  }

  private fun positionReachedInternal(suspendContext: XSuspendContext, attract: Boolean) {
    if (handlePositionReaching(suspendContext, attract)) {
      return
    }

    setBreakpointsDisabledTemporarily(false)
    updateSuspendContext(suspendContext)

    val topFramePosition = getTopFramePosition()
    logPositionReached(topFramePosition)

    val needsInitialization = myTabInitDataFlow.value == null
    if (needsInitialization || attract) {
      invokeLaterIfProjectAlive(myProject, Runnable {
        if (needsInitialization) {
          initSessionTab(null, true)
        }
        val topFrameIsAbsent = topFramePosition == null
        if (useFeProxy()) {
          myPausedEvents.tryEmit(XDebugSessionPausedInfo(attract, topFrameIsAbsent))
        }
        else {
          // We have to keep this code because Code with Me expects BE to work with tab similar to monolith
          mySessionTab!!.onPause(attract, topFrameIsAbsent)
        }
      })
    }

    myDispatcher.getMulticaster().sessionPaused()
  }

  @ApiStatus.Internal
  fun updateSuspendContext(newSuspendContext: XSuspendContext) {
    suspendContextFlow.value = newSuspendContext
    this.currentSuspendCoroutineScope = newSuspendContext.coroutineScope ?: provideSuspendScope(this)
    this.currentExecutionStack = newSuspendContext.activeExecutionStack
    val newCurrentStackFrame = currentExecutionStack?.topFrame
    myCurrentStackFrameManager.setCurrentStackFrame(newCurrentStackFrame)
    myIsTopFrame = true
    myTopStackFrame.value = newCurrentStackFrame

    val isSteppingSuspendContext = newSuspendContext is XSteppingSuspendContext

    myPaused.value = !isSteppingSuspendContext

    if (!isSteppingSuspendContext) {
      val isAlternative = myAlternativeSourceHandler?.isAlternativeSourceKindPreferred(newSuspendContext) == true
      updateExecutionPosition(if (isAlternative) XSourceKind.ALTERNATIVE else XSourceKind.MAIN)
    }
  }

  override fun positionReached(suspendContext: XSuspendContext) {
    positionReached(suspendContext, false)
  }

  fun positionReached(suspendContext: XSuspendContext, attract: Boolean) {
    clearActiveNonLineBreakpoint()
    positionReachedInternal(suspendContext, attract)
  }

  override fun sessionResumed() {
    doResume()
  }

  @get:ApiStatus.Internal
  val isStoppedState: StateFlow<Boolean>
    get() = myStopped

  override fun isStopped(): Boolean {
    return myStopped.value
  }

  private fun stopImpl() {
    if (!myStopped.compareAndSet(false, true)) {
      return
    }

    try {
      removeBreakpointListeners()
    }
    finally {
      myDebugProcess!!.stopAsync()
        .onSuccess(Consumer { aVoid: Any? ->
          processStopped()
        })
    }
  }

  private fun processStopped() {
    if (!myProject.isDisposed()) {
      myProject.getMessageBus().syncPublisher<XDebuggerManagerListener>(XDebuggerManager.TOPIC).processStopped(myDebugProcess!!)
    }

    if (!isTabInitialized && myConsoleView != null) {
      invokeOnEdt(Runnable { Disposer.dispose(myConsoleView!!) })
    }

    clearPausedData()

    if (myValueMarkers != null) {
      myValueMarkers!!.clear()
    }
    if (XDebuggerSettingManagerImpl.getInstanceImpl().generalSettings.isUnmuteOnStop) {
      sessionData.isBreakpointsMuted = false
    }
    myDebuggerManager.removeSession(this)
    XDebugSessionProxyKeeper.getInstance(project).removeProxy(this)
    myDispatcher.getMulticaster().sessionStopped()
    myDispatcher.getListeners().clear()

    myProject.putUserData(InlineDebugRenderer.LinePainter.CACHE, null)

    synchronized(myRegisteredBreakpoints) {
      myRegisteredBreakpoints.clear()
    }

    coroutineScope.cancel(null)
  }

  @ApiStatus.Internal
  fun getRunContentDescriptorIfInitialized(): RunContentDescriptor? {
    return myRunContentDescriptor
  }

  private fun removeBreakpointListeners() {
    val breakpointListenerDisposable = myBreakpointListenerDisposable
    if (breakpointListenerDisposable != null) {
      myBreakpointListenerDisposable = null
      Disposer.dispose(breakpointListenerDisposable)
    }
  }

  fun isInactiveSlaveBreakpoint(breakpoint: XBreakpoint<*>?): Boolean {
    return myInactiveSlaveBreakpoints.contains(breakpoint)
  }

  override fun stop() {
    stop(myDebugProcess)
  }

  private fun stop(process: XDebugProcess?) {
    val processHandler = process?.getProcessHandler()
    if (processHandler == null || processHandler.isProcessTerminated || processHandler.isProcessTerminating) return

    if (processHandler.detachIsDefault()) {
      processHandler.detachProcess()
    }
    else {
      processHandler.destroyProcess()
    }
  }

  override fun reportError(message: String) {
    reportMessage(message, MessageType.ERROR)
  }

  override fun reportMessage(message: String, type: MessageType) {
    reportMessage(message, type, null)
  }

  override fun reportMessage(message: String, type: MessageType, listener: HyperlinkListener?) {
    val notification = XDebuggerManagerImpl.getNotificationGroup().createNotification(message, type.toNotificationType())
    if (listener != null) {
      notification.setListener(NotificationListener { _: Notification?, event: HyperlinkEvent? ->
        if (event!!.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          listener.hyperlinkUpdate(event)
        }
      })
    }
    notification.notify(myProject)
  }

  private inner class MyBreakpointListener : XBreakpointListener<XBreakpoint<*>?> {
    override fun breakpointAdded(breakpoint: XBreakpoint<*>) {
      if (processAdd(breakpoint)) {
        val presentation = getBreakpointPresentation(breakpoint)
        if (presentation != null) {
          if (XDebuggerUtilImpl.getVerifiedIcon(breakpoint) == presentation.icon) {
            reportBreakpointVerified(breakpoint, 0)
          }
          else {
            presentation.timestamp = System.currentTimeMillis()
          }
        }
      }
    }

    override fun breakpointRemoved(breakpoint: XBreakpoint<*>) {
      checkActiveNonLineBreakpointOnRemoval(breakpoint)
      processRemove(breakpoint)
    }

    fun processRemove(breakpoint: XBreakpoint<*>) {
      processAllHandlers(breakpoint, false)
    }

    fun processAdd(breakpoint: XBreakpoint<*>): Boolean {
      if (!myBreakpointsDisabled) {
        processAllHandlers(breakpoint, true)
        return true
      }
      return false
    }

    override fun breakpointChanged(breakpoint: XBreakpoint<*>) {
      processRemove(breakpoint)
      processAdd(breakpoint)
    }
  }

  private inner class MyDependentBreakpointListener : XDependentBreakpointListener {
    override fun dependencySet(slave: XBreakpoint<*>, master: XBreakpoint<*>) {
      val added = myInactiveSlaveBreakpoints.add(slave)
      if (added) {
        processAllHandlers(slave, false)
      }
    }

    override fun dependencyCleared(breakpoint: XBreakpoint<*>) {
      val removed = myInactiveSlaveBreakpoints.remove(breakpoint)
      if (removed) {
        processAllHandlers(breakpoint, true)
      }
    }
  }

  /**
   * Configuration name is either configuration type ID or session name.
   *
   *
   * Configuration type ID is the preferred way to reuse watches within the same configuration type,
   * e.g., in different test run configurations.
   */
  private fun computeConfigurationName(): String {
    if (this.executionEnvironment != null) {
      val profile = executionEnvironment.runProfile
      if (profile is RunConfiguration) {
        return profile.getType().getId()
      }
    }
    return sessionName
  }

  private fun rememberUserActionStart(action: String) {
    myUserRequestStart = System.currentTimeMillis()
    myUserRequestAction = action
  }

  private fun logPositionReached(topFramePosition: XSourcePosition?) {
    val fileType = topFramePosition?.getFile()?.fileType
    if (myUserRequestAction != null) {
      val durationMs = System.currentTimeMillis() - myUserRequestStart
      if (PERFORMANCE_LOG.isDebugEnabled()) {
        PERFORMANCE_LOG.debug("Position reached in " + durationMs + "ms")
      }
      XDebuggerPerformanceCollector.logExecutionPointReached(myProject, fileType, myUserRequestAction!!, durationMs)
      myUserRequestAction = null
    }
    else {
      logBreakpointReached(myProject, fileType)
    }
  }

  private fun handlePositionReaching(context: XSuspendContext, attract: Boolean): Boolean {
    return isMixedMode && (myDebugProcess as XMixedModeCombinedDebugProcess).handlePositionReached(context, attract)
  }

  companion object {
    private val LOG = Logger.getInstance(XDebugSessionImpl::class.java)
    private val PERFORMANCE_LOG = Logger.getInstance("#com.intellij.xdebugger.impl.XDebugSessionImpl.performance")

    // TODO[eldar] needed to workaround nullable myAlternativeSourceHandler.
    private val ALWAYS_FALSE_STATE = MutableStateFlow<Boolean>(false).asStateFlow<Boolean>()

    //need to compile under 1.8, please do not remove before checking
    private fun getBreakpointTypeClass(handler: XBreakpointHandler<*>): XBreakpointType<*, *>? {
      return XDebuggerUtil.getInstance().findBreakpointType(handler.getBreakpointTypeClass())
    }
  }
}
