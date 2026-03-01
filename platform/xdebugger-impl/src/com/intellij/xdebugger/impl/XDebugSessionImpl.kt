// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.diagnostic.logging.LogConsoleManager
import com.intellij.diagnostic.logging.LogFilesManager
import com.intellij.execution.Executor
import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.execution.impl.RUN_CONTENT_DESCRIPTOR_LIFECYCLE_TOPIC
import com.intellij.execution.impl.RunContentDescriptorLifecycleListener
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.rpc.toDto
import com.intellij.execution.runners.BackendExecutionEnvironmentProxy
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.RunTab
import com.intellij.execution.storeGlobally
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.ui.icons.rpcId
import com.intellij.idea.AppMode
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.platform.debugger.impl.rpc.XDebugSessionDataId
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.platform.debugger.impl.rpc.XDebugSessionPausedInfo
import com.intellij.platform.debugger.impl.rpc.XDebugTabLayouterDto
import com.intellij.platform.debugger.impl.rpc.XDebuggerSessionTabAbstractInfo
import com.intellij.platform.debugger.impl.rpc.XDebuggerSessionTabInfo
import com.intellij.platform.debugger.impl.rpc.XDebuggerSessionTabInfoNoInit
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive
import com.intellij.ui.AppUIUtil.invokeOnEdt
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.EventDispatcher
import com.intellij.util.SmartList
import com.intellij.util.ThrowableRunnable
import com.intellij.util.application
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xdebugger.DapMode
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.XAlternativeSourceHandler
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessDebuggeeInForeground
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XDebuggerPerformanceCollector.logBreakpointReached
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.breakpoints.BreakpointsUsageCollector.reportBreakpointVerified
import com.intellij.xdebugger.impl.breakpoints.CustomizedBreakpointPresentation
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil.getShortText
import com.intellij.xdebugger.impl.breakpoints.XDependentBreakpointListener
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl
import com.intellij.xdebugger.impl.evaluate.ValueLookupManagerController
import com.intellij.xdebugger.impl.frame.XValueMarkers
import com.intellij.xdebugger.impl.inline.DebuggerInlayListener
import com.intellij.xdebugger.impl.inline.InlineDebugRenderer
import com.intellij.xdebugger.impl.mixedmode.XMixedModeCombinedDebugProcess
import com.intellij.xdebugger.impl.proxy.asProxy
import com.intellij.xdebugger.impl.rpc.models.RunnerLayoutUiBridge
import com.intellij.xdebugger.impl.rpc.models.XDebugSessionAdditionalTabComponentManager
import com.intellij.xdebugger.impl.rpc.models.XDebugTabLayouterModel
import com.intellij.xdebugger.impl.rpc.models.XSuspendContextModel
import com.intellij.xdebugger.impl.rpc.models.storeGlobally
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl
import com.intellij.xdebugger.impl.ui.XDebugSessionData
import com.intellij.xdebugger.impl.ui.XDebugSessionTab
import com.intellij.xdebugger.impl.ui.allowFramesViewCustomization
import com.intellij.xdebugger.impl.ui.forceShowNewDebuggerUi
import com.intellij.xdebugger.impl.ui.getDefaultFramesViewKey
import com.intellij.xdebugger.impl.util.start
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant
import com.intellij.xdebugger.ui.IXDebuggerSessionTab
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import javax.swing.Icon
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

  @ApiStatus.Internal
  val tabCoroutineScope: CoroutineScope = debuggerManager.coroutineScope.childScope("XDebugger session tab $sessionName")

  val id: XDebugSessionId = storeGlobally(coroutineScope)

  private var myDebugProcess: XDebugProcess? = null
  private val myRegisteredBreakpoints: MutableMap<XBreakpoint<*>?, CustomizedBreakpointPresentation?> = HashMap<XBreakpoint<*>?, CustomizedBreakpointPresentation?>()
  private val myInactiveSlaveBreakpoints: MutableSet<XBreakpoint<*>?> = Collections.synchronizedSet<XBreakpoint<*>?>(HashSet())
  private var myBreakpointsDisabled = false
  private val myDebuggerManager: XDebuggerManagerImpl = debuggerManager
  private var myBreakpointListenerDisposable: Disposable? = null

  private var myAlternativeSourceHandler: XAlternativeSourceHandler? = null
  private var myIsTopFrame = false

  private val myPaused = MutableStateFlow(false)
  private var myValueMarkers: XValueMarkers<*, *>? = null
  private val mySessionName: @Nls String = sessionName
  private val mySessionTab = CompletableDeferred<XDebugSessionTab?>()
  private var myMockRunContentDescriptor: RunContentDescriptor? = null
  val sessionData: XDebugSessionData

  @ApiStatus.Internal
  val sessionDataId: XDebugSessionDataId

  private val myActiveNonLineBreakpointAndPositionFlow = MutableStateFlow<Pair<XBreakpoint<*>, XSourcePosition?>?>(null)
  private val myPausedEvents = MutableSharedFlow<XDebugSessionPausedInfo>(replay = 1, extraBufferCapacity = 1)
  private val myShowTabDeferred = CompletableDeferred<Unit>()
  private val myDispatcher = EventDispatcher.create(XDebugSessionListener::class.java)
  private val myProject: Project = debuggerManager.project

  private val executionEnvironment: ExecutionEnvironment? = environment
  override fun getExecutionEnvironment(): ExecutionEnvironment? = executionEnvironment

  private val myStopped = MutableStateFlow(false)
  private val myReadOnly = MutableStateFlow(false)
  private val myStepOverActionAllowed = MutableStateFlow(true)
  private val myStepOutActionAllowed = MutableStateFlow(true)
  private val myRunToCursorActionAllowed = MutableStateFlow(true)

  private val myShowToolWindowOnSuspendOnly: Boolean = showToolWindowOnSuspendOnly
  private val myTabInitDataFlow = MutableStateFlow<XDebuggerSessionTabAbstractInfo?>(null)
  val restartActions: MutableList<AnAction> = SmartList<AnAction>()
  val extraStopActions: MutableList<AnAction> = SmartList<AnAction>()
  val extraActions: MutableList<AnAction> = SmartList<AnAction>()
  private var myConsoleView: ConsoleView? = null
  private val myIcon: Icon? = icon

  @Volatile
  private var currentStackFrame: XStackFrame? = null

  // Ref is used to prevent StateFlow's equals checks
  private val topStackFrame = MutableStateFlow<Ref<XStackFrame>?>(null)

  var currentExecutionStack: XExecutionStack? = null
  private val suspendContextModel = AtomicReference<XSuspendContextModel?>(null)
  private val sessionInitializedDeferred = CompletableDeferred<Unit>()

  @Volatile
  private var breakpointsInitialized = false
  private var myUserRequestStart: Long = 0
  private var myUserRequestAction: String? = null

  private val myActiveNonLineBreakpointFlow = myActiveNonLineBreakpointAndPositionFlow
    .combine(topStackFrame) { breakpointAndPosition, _ ->
      val (breakpoint, breakpointPosition) = breakpointAndPosition ?: return@combine null
      if (breakpointPosition == null) return@combine breakpoint
      val position = topFramePosition ?: return@combine null
      val samePosition = breakpointPosition.getFile() == position.getFile() && breakpointPosition.getLine() == position.getLine()
      breakpoint.takeIf { !samePosition }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, null)

  init {
    var contentToReuse = contentToReuse
    ValueLookupManagerController.getInstance(myProject).startListening()

    if (!DapMode.isDap()) {
      DebuggerInlayListener.getInstance(myProject).startListening()
    }

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
      oldSessionData = XDebugSessionData(currentConfigurationName)
    }
    this.sessionData = oldSessionData
    this.sessionDataId = sessionData.storeGlobally(tabCoroutineScope, this)
  }

  override fun getSessionName(): String {
    return mySessionName
  }

  @get:ApiStatus.Internal
  val tabInitDataFlow: Flow<XDebuggerSessionTabAbstractInfo>
    get() = myTabInitDataFlow.filterNotNull()

  @Deprecated("Deprecated in Java")
  override fun getRunContentDescriptor(): RunContentDescriptor {
    if (!application.isUnitTestMode && SplitDebuggerMode.showSplitWarnings()) {
      LOG.error("[Split debugger] RunContentDescriptor should not be used in split mode from XDebugSession. " +
                "XDebugSession.getRunContentDescriptor is deprecated, see the javadoc for details")
    }
    val descriptor = getMockRunContentDescriptorIfInitialized()
    LOG.assertTrue(descriptor != null, "Run content descriptor is not initialized yet!")
    return descriptor!!
  }

  /**
   * This method relies on creation of a mock [RunContentDescriptor] on backend when in split mode.
   * The descriptor returned from this method is not registered in the [com.intellij.execution.ui.RunContentManagerImpl] and is not shown in the UI.
   * To access the UI-visible [RunContentDescriptor], use [XDebugSessionProxy.sessionTab] instead.
   */
  @ApiStatus.Internal
  fun getMockRunContentDescriptorIfInitialized(): RunContentDescriptor? {
    return myMockRunContentDescriptor
  }

  val hasSessionTab: Boolean get() = mySessionTab.isCompleted

  private val isTabInitialized: Boolean
    get() = myTabInitDataFlow.value != null && (SplitDebuggerMode.isSplitDebugger() || hasSessionTab)

  private fun assertSessionTabInitialized() {
    val initialized = isTabInitialized
    if (myShowToolWindowOnSuspendOnly) {
      LOG.assertTrue(initialized, "Debug tool window isn't shown yet because debug process isn't suspended")
    }
    else {
      LOG.assertTrue(initialized, "Debug tool window not initialized yet!")
    }
  }

  override fun setPauseActionSupported(isSupported: Boolean) {
    if (sessionData.isPauseSupported == isSupported) return
    sessionData.isPauseSupported = isSupported
    myDispatcher.getMulticaster().settingsChanged()
  }

  var isReadOnly: Boolean
    get() = myReadOnly.value
    set(readOnly) {
      myReadOnly.value = readOnly
    }

  @get:ApiStatus.Internal
  @set:ApiStatus.Internal
  var isStepOverActionAllowed: Boolean
    get() = myStepOverActionAllowed.value
    set(value) {
      myStepOverActionAllowed.value = value
    }

  @get:ApiStatus.Internal
  @set:ApiStatus.Internal
  var isStepOutActionAllowed: Boolean
    get() = myStepOutActionAllowed.value
    set(value) {
      myStepOutActionAllowed.value = value
    }

  @get:ApiStatus.Internal
  @set:ApiStatus.Internal
  var isRunToCursorActionAllowed: Boolean
    get() = myRunToCursorActionAllowed.value
    set(value) {
      myRunToCursorActionAllowed.value = value
    }

  fun addRestartActions(vararg restartActions: AnAction) {
    safeAddAll(this.restartActions, *restartActions)
  }

  fun addExtraActions(vararg extraActions: AnAction) {
    safeAddAll(this.extraActions, *extraActions)
  }

  // used externally
  @Suppress("unused")
  fun addExtraStopActions(vararg extraStopActions: AnAction) {
    safeAddAll(this.extraStopActions, *extraStopActions)
  }

  private fun <T> safeAddAll(collection: MutableList<T>, vararg elements: T) {
    for (e in elements) {
      if (e == null) {
        LOG.error("Null element found in safeAddAll: ${elements.toList()}")
        continue
      }
      collection.add(e)
    }
  }

  override fun rebuildViews() {
    myDispatcher.getMulticaster().settingsChanged()
  }

  fun frontendUpdate() {
    myDispatcher.getMulticaster().settingsChangedFromFrontend()
  }

  override fun getRunProfile(): RunProfile? {
    return if (this.executionEnvironment != null) executionEnvironment.runProfile else null
  }

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
    return isPaused && suspendContext != null
  }

  @ApiStatus.Internal
  fun getPausedEventsFlow(): Flow<XDebugSessionPausedInfo> {
    return myPausedEvents
  }

  override fun isPaused(): Boolean {
    return myPaused.value
  }

  @ApiStatus.Internal
  fun sessionInitializedDeferred(): Deferred<Unit> {
    return sessionInitializedDeferred
  }

  override fun getCurrentStackFrame(): XStackFrame? {
    return currentStackFrame
  }

  override fun getSuspendContext(): XSuspendContext? {
    return suspendContextModel.get()?.suspendContext
  }

  /**
   * Returns the current [XSuspendContextModel], or `null` if the session is not suspended.
   *
   * [XSuspendContextModel] provides [CoroutineScope] that is attached to the current suspension context and [id].
   */
  @ApiStatus.Internal
  fun getSuspendContextModel(): XSuspendContextModel? {
    return suspendContextModel.get()
  }

  override fun getCurrentPosition(): XSourcePosition? {
    return getFrameSourcePosition(currentStackFrame)
  }

  override fun getTopFramePosition(): XSourcePosition? {
    return getFrameSourcePosition(topStackFrame.value?.get())
  }

  fun getFrameSourcePosition(frame: XStackFrame?): XSourcePosition? {
    return getFrameSourcePosition(frame, this.currentSourceKind)
  }

  fun getFrameSourcePosition(frame: XStackFrame?, sourceKind: XSourceKind): XSourcePosition? {
    if (frame == null) return null
    return when (sourceKind) {
      XSourceKind.MAIN -> frame.sourcePosition
      XSourceKind.ALTERNATIVE -> myAlternativeSourceHandler?.getAlternativePosition(frame)
    }
  }

  val currentSourceKind: XSourceKind
    get() {
      val state = this.alternativeSourceKindState
      return if (state.value) XSourceKind.ALTERNATIVE else XSourceKind.MAIN
    }

  val alternativeSourceKindState: StateFlow<Boolean>
    get() = myAlternativeSourceHandler?.getAlternativeSourceKindState() ?: ALWAYS_FALSE_STATE

  fun init(process: XDebugProcess, contentToReuse: RunContentDescriptor?) {
    LOG.assertTrue(myDebugProcess == null)
    myDebugProcess = process
    myAlternativeSourceHandler = process.alternativeSourceHandler
    XDebugManagerProxy.getInstance().getDebuggerExecutionPointManager(project)?.alternativeSourceKindFlow = this.alternativeSourceKindState

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
    if (!DapMode.isDap()) {
      myConsoleView = process.createConsole() as ConsoleView
      if (!myShowToolWindowOnSuspendOnly) {
        initSessionTab(contentToReuse, false)
      }
    }
    sessionInitializedDeferred.complete(Unit)
  }

  fun reset() {
    breakpointsInitialized = false
    removeBreakpointListeners()
    myPaused.value = false
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

  /**
   * Use [runWhenTabReady] to avoid races.
   */
  val sessionTab: XDebugSessionTab?
    get() {
      if (SplitDebuggerMode.showSplitWarnings()) {
        // See "TODO [Debugger.sessionTab]" to see usages which are not yet properly migrated.
        LOG.error("[Split debugger] Debug tab should not be used in split mode from XDebugSession")
      }
      return getSessionTabInternal()
    }

  /**
   * Calls [block] in EDT when the tab is ready.
   */
  @ApiStatus.Obsolete
  fun runWhenTabReady(block: (XDebugSessionTab?) -> Unit) {
    if (AppMode.isRemoteDevHost() && SplitDebuggerMode.isSplitDebugger()) {
      if (SplitDebuggerMode.showSplitWarnings()) {
        LOG.error("[Split debugger] Debugger tab is not accessible in RemDev on backend")
      }
      return
    }
    assertSessionTabInitialized()
    tabCoroutineScope.launch(Dispatchers.EDT) {
      val tab = mySessionTab.await()
      block(tab)
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun getSessionTabInternal(): XDebugSessionTab? = if (mySessionTab.isCompleted) mySessionTab.getCompleted() else null

  /**
   * Use [runWhenUiReady] to avoid races.
   *
   * See [XDebugSession.getUI] doc for proper migration steps.
   */
  @ApiStatus.Obsolete
  override fun getUI(): RunnerLayoutUi? {
    assertSessionTabInitialized()
    if (SplitDebuggerMode.showSplitWarnings()) {
      // See "TODO [Debugger.RunnerLayoutUi]" to see usages which are not yet properly migrated.
      LOG.warn("[Split debugger] RunnerLayoutUi should not be used in split mode from XDebugSession")
    }
    return if (SplitDebuggerMode.isSplitDebugger() && AppMode.isRemoteDevHost()) {
      getMockRunContentDescriptorIfInitialized()?.runnerLayoutUi
    }
    else {
      getSessionTabInternal()?.ui
    }
  }

  /**
   * Calls [block] in EDT when the tab UI is ready.
   *
   * See [XDebugSession.getUI] doc for proper migration steps.
   */
  @ApiStatus.Obsolete
  fun runWhenUiReady(block: (RunnerLayoutUi) -> Unit) {
    tabCoroutineScope.launch(Dispatchers.EDT) {
      assertSessionTabInitialized()
      val ui = if (SplitDebuggerMode.isSplitDebugger() && AppMode.isRemoteDevHost()) {
        getMockRunContentDescriptorIfInitialized()?.runnerLayoutUi
      }
      else {
        mySessionTab.await()?.ui
      }
      if (ui != null) {
        block(ui)
      }
    }
  }

  override fun isMixedMode(): Boolean {
    return myDebugProcess is XMixedModeCombinedDebugProcess
  }

  /**
   * TODO When we move to RD-first approach, @RequiresEdt requirements in [XDebuggerManager] can be removed
   */
  @OptIn(AwaitCancellationAndInvoke::class)
  private fun initSessionTab(contentToReuse: RunContentDescriptor?, shouldShowTab: Boolean) {
    val forceNewDebuggerUi = debugProcess.forceShowNewDebuggerUi()
    val withFramesCustomization = debugProcess.allowFramesViewCustomization()
    val defaultFramesViewKey: String? = debugProcess.getDefaultFramesViewKey()

    if (SplitDebuggerMode.isSplitDebugger()) {
      if (shouldShowTab) {
        myShowTabDeferred.complete(Unit)
      }
      val localTabScope = tabCoroutineScope.childScope("ExecutionEnvironmentDto")
      val tabClosedChannel = Channel<Unit>()
      val additionalTabComponentManager = XDebugSessionAdditionalTabComponentManager(localTabScope)
      val runContentDescriptorId = CompletableDeferred<RunContentDescriptorIdImpl>()
      val tabLayouterDto = CompletableDeferred<XDebugTabLayouterDto>()
      val executionEnvironmentId = executionEnvironment?.storeGlobally(localTabScope)

      val tabInfo = XDebuggerSessionTabInfo(myIcon?.rpcId(), forceNewDebuggerUi, withFramesCustomization, defaultFramesViewKey,
                                            executionEnvironmentId, executionEnvironment?.toDto(localTabScope),
                                            additionalTabComponentManager.id, tabClosedChannel,
                                            runContentDescriptorId, myShowTabDeferred, tabLayouterDto)
      if (myTabInitDataFlow.compareAndSet(null, tabInfo)) {
        // This is a mock tab used in backend only
        // Using a RunTab as a mock component let us reuse context reusing,
        // e.g. execution environment is present in the context of the mock descriptor
        val runTab = object : RunTab(project, GlobalSearchScope.allScope(project),
                                     "Debug", "Debug", sessionName) {
          init {
            myEnvironment = executionEnvironment
            myUi.getContentManager().addUiDataProvider { sink ->
              sink[XDebugSessionData.DATA_KEY] = sessionData
            }
          }

          val component get() = myUi.component
          val ui get() = myUi

          val consoleManger = createLogConsoleManager(additionalTabComponentManager) { debugProcess.processHandler }
        }
        val disposable = localTabScope.asDisposable()
        addAdditionalTabsAndConsolesToManager(runTab.consoleManger, disposable)

        val layoutBridge = RunnerLayoutUiBridge(project, disposable)
        // This is a mock descriptor used in backend only
        val mockDescriptor = object : RunContentDescriptor(myConsoleView, debugProcess.getProcessHandler(), runTab.component,
                                                           sessionName, myIcon, null) {
          init {
            runnerLayoutUi = if (AppMode.isRemoteDevHost()) layoutBridge else runTab.ui
          }

          override fun isHiddenContent(): Boolean = true
        }
        Disposer.register(disposable, runTab)
        Disposer.register(disposable, mockDescriptor)
        val descriptorId = mockDescriptor.storeGlobally(localTabScope)
        runContentDescriptorId.complete(descriptorId)
        mockDescriptor.id = descriptorId

        val tabLayouter = debugProcess.createTabLayouter()
        val tabLayouterId = XDebugTabLayouterModel(tabLayouter, layoutBridge).storeGlobally(localTabScope)
        tabLayouterDto.complete(XDebugTabLayouterDto(tabLayouterId, tabLayouter))

        debuggerManager.coroutineScope.launch(start = CoroutineStart.ATOMIC) {
          try {
            tabClosedChannel.receiveCatching()
          }
          finally {
            tabClosedChannel.close()
            tabCoroutineScope.cancel()
          }
        }
        myMockRunContentDescriptor = mockDescriptor
        myDebugProcess!!.sessionInitialized()
        project.messageBus.connect(localTabScope).subscribe(RUN_CONTENT_DESCRIPTOR_LIFECYCLE_TOPIC, object : RunContentDescriptorLifecycleListener {
          override fun beforeContentShown(descriptor: RunContentDescriptor, executor: Executor) {
            if (descriptor === mockDescriptor) {
              myShowTabDeferred.complete(Unit)
            }
          }

          override fun afterContentShown(descriptor: RunContentDescriptor, executor: Executor) {
          }
        })
      }
      else {
        localTabScope.cancel()
        tabClosedChannel.close()
      }
    }
    else {
      if (myTabInitDataFlow.compareAndSet(null, XDebuggerSessionTabInfoNoInit)) {
        val proxy = this.asProxy()
        val tab = XDebugSessionTab.create(proxy, myIcon, executionEnvironment?.let { BackendExecutionEnvironmentProxy(it) }, contentToReuse,
                                          forceNewDebuggerUi, withFramesCustomization, defaultFramesViewKey)
        tabInitialized(tab)
        myMockRunContentDescriptor = tab.runContentDescriptor
        myDebugProcess!!.sessionInitialized()
        if (shouldShowTab) {
          tab.showTab()
        }
      }
    }
  }

  private fun addAdditionalTabsAndConsolesToManager(
    consoleManager: LogConsoleManager,
    disposable: Disposable,
  ) {
    val runConfiguration = executionEnvironment?.runProfile
    if (runConfiguration is RunConfigurationBase<*>) {
      val logFilesManager = LogFilesManager(project, consoleManager, disposable)
      // Triggers additional tabs creation along with consoles via createAdditionalTabComponents
      logFilesManager.addLogConsoles(runConfiguration, debugProcess.processHandler)
    }
  }

  @ApiStatus.Internal
  fun tabInitialized(sessionTab: IXDebuggerSessionTab?) {
    mySessionTab.complete(sessionTab as? XDebugSessionTab)
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
    if (SplitDebuggerMode.isSplitDebugger()) {
      myShowTabDeferred.complete(Unit)
    }
    else {
      sessionTab?.showTab()
    }
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
      val active = ReadAction.computeBlocking<Boolean, RuntimeException?>(ThrowableComputable { isBreakpointActive(b!!) })
      if (active) {
        synchronized(myRegisteredBreakpoints) {
          myRegisteredBreakpoints[b] = CustomizedBreakpointPresentation()
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
    val context = suspendContext
    clearPausedData()
    myDispatcher.getMulticaster().sessionResumed()
    return context
  }

  private fun clearPausedData() {
    val oldSuspendContextModel = suspendContextModel.getAndSet(null)
    oldSuspendContextModel?.cancel()
    this.currentExecutionStack = null
    currentStackFrame = null
    topStackFrame.value = null
    clearActiveNonLineBreakpoint()
  }

  @Deprecated("Update should go via front-end listeners")
  override fun updateExecutionPosition() {
    // Actually, it is just a fallback. All information should go via front-end listeners.
    updateExecutionPosition(this.asProxy())
  }

  val isTopFrameSelected: Boolean
    get() = this.currentExecutionStack != null && myIsTopFrame


  override fun showExecutionPoint() {
    val currentSuspendContext = suspendContext ?: return
    val executionStack = currentSuspendContext.activeExecutionStack ?: return
    val topFrame = executionStack.getTopFrame() ?: return
    setCurrentStackFrame(executionStack, topFrame, true)
  }

  override fun setCurrentStackFrame(executionStack: XExecutionStack, frame: XStackFrame, isTopFrame: Boolean) {
    val currentSuspendContext = suspendContext ?: return
    setCurrentStackFrame(currentSuspendContext, executionStack, frame, isTopFrame, false)
  }

  @ApiStatus.Internal
  fun setCurrentStackFrame(
    expectedSuspendContext: XSuspendContext,
    executionStack: XExecutionStack,
    frame: XStackFrame,
    isTopFrame: Boolean,
    changedByUser: Boolean,
  ) {
    val currentContext = suspendContext ?: return
    if (expectedSuspendContext !== currentContext) return

    val frameChanged = currentStackFrame !== frame
    this.currentExecutionStack = executionStack
    currentStackFrame = frame
    myIsTopFrame = isTopFrame

    if (frameChanged) {
      myDispatcher.getMulticaster().stackFrameChanged(changedByUser)
    }

    if (myDebuggerManager.currentSession == this) {
      activateSession(frameChanged)
    }
  }

  fun activateSession(forceUpdateExecutionPosition: Boolean) {
    myDebuggerManager.setCurrentSession(this)
  }

  val activeNonLineBreakpoint: XBreakpoint<*>? get() = myActiveNonLineBreakpointFlow.value
  val activeNonLineBreakpointFlow: StateFlow<XBreakpoint<*>?> get() = myActiveNonLineBreakpointFlow

  fun checkActiveNonLineBreakpointOnRemoval(removedBreakpoint: XBreakpoint<*>) {
    val (breakpoint, _) = myActiveNonLineBreakpointAndPositionFlow.value ?: return
    if (breakpoint === removedBreakpoint) {
      clearActiveNonLineBreakpoint()
    }
  }

  private fun clearActiveNonLineBreakpoint() {
    myActiveNonLineBreakpointAndPositionFlow.value = null
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
    if (SplitDebuggerMode.isSplitDebugger() && breakpoint is XLineBreakpointImpl<*>) {
      // for useFeProxy we call update directly since visual presentation is disabled on the backend
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
        if (needsInitialization && !DapMode.isDap()) {
          initSessionTab(null, true)
        }
        val topFrameIsAbsent = topFramePosition == null
        if (SplitDebuggerMode.isSplitDebugger()) {
          myPausedEvents.tryEmit(XDebugSessionPausedInfo(attract, topFrameIsAbsent))
        }
        else {
          // We have to keep this code because Code with Me expects BE to work with tab similar to monolith
          sessionTab?.onPause(attract, topFrameIsAbsent)
        }
      })
    }

    myDispatcher.getMulticaster().sessionPaused()
  }

  @ApiStatus.Internal
  fun updateSuspendContext(newSuspendContext: XSuspendContext) {
    val newModel = XSuspendContextModel(coroutineScope, newSuspendContext, this)
    val oldModel = suspendContextModel.getAndSet(newModel)
    oldModel?.cancel()

    this.currentExecutionStack = newSuspendContext.activeExecutionStack
    val newCurrentStackFrame = currentExecutionStack?.topFrame
    currentStackFrame = newCurrentStackFrame
    myIsTopFrame = true
    topStackFrame.value = Ref(newCurrentStackFrame)

    val isSteppingSuspendContext = newSuspendContext is XSteppingSuspendContext

    myPaused.value = !isSteppingSuspendContext
  }

  override fun positionReached(suspendContext: XSuspendContext) {
    positionReached(suspendContext, false)
  }

  override fun positionReached(suspendContext: XSuspendContext, attract: Boolean) {
    clearActiveNonLineBreakpoint()
    positionReachedInternal(suspendContext, attract)
  }

  override fun sessionResumed() {
    doResume()
  }

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
      myDebugProcess!!.stopAsync().onSuccess { processStopped() }
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
    myDispatcher.getMulticaster().sessionStopped()
    myDispatcher.getListeners().clear()

    myProject.putUserData(InlineDebugRenderer.LinePainter.CACHE, null)

    synchronized(myRegisteredBreakpoints) {
      myRegisteredBreakpoints.clear()
    }

    coroutineScope.cancel(null)
    if (!isTabInitialized) {
      // Tab was not created during session running
      tabCoroutineScope.cancel()
    }
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
