// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.execution.process.ProcessHandler
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
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.createFrontendXDebuggerEvaluator
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.EventDispatcher
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.frame.XValueMarkers
import com.intellij.xdebugger.impl.rpc.*
import com.intellij.xdebugger.impl.ui.XDebugSessionData
import com.intellij.xdebugger.impl.ui.XDebugSessionTab
import com.intellij.xdebugger.ui.XDebugTabLayouter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.swing.event.HyperlinkListener

internal class FrontendXDebuggerSession private constructor(
  override val project: Project,
  scope: CoroutineScope,
  sessionDto: XDebugSessionDto,
  override val consoleView: ConsoleView?,
) : XDebugSessionProxy {
  private val cs = scope.childScope("Session ${sessionDto.id}")
  private val localEditorsProvider = sessionDto.editorsProviderDto.editorsProvider
  private val eventsDispatcher = EventDispatcher.create(XDebugSessionListener::class.java)
  val id = sessionDto.id
  val evaluator: StateFlow<FrontendXDebuggerEvaluator?> =
    channelFlow {
      XDebugSessionApi.getInstance().currentEvaluator(id).collectLatest { evaluatorDto ->
        if (evaluatorDto == null) {
          send(null)
          return@collectLatest
        }
        supervisorScope {
          val evaluator = createFrontendXDebuggerEvaluator(project, this, evaluatorDto)
          send(evaluator)
          awaitCancellation()
        }
      }
    }.stateIn(cs, SharingStarted.Eagerly, null)

  val sourcePosition: StateFlow<XSourcePosition?> =
    channelFlow {
      XDebugSessionApi.getInstance().currentSourcePosition(id).collectLatest { sourcePositionDto ->
        if (sourcePositionDto == null) {
          send(null)
          return@collectLatest
        }
        supervisorScope {
          send(sourcePositionDto.sourcePosition())
          awaitCancellation()
        }
      }
    }.stateIn(cs, SharingStarted.Eagerly, null)

  private val sessionState: StateFlow<XDebugSessionState> =
    channelFlow {
      XDebugSessionApi.getInstance().currentSessionState(id).collectLatest { sessionState ->
        send(sessionState)
      }
    }.stateIn(cs, SharingStarted.Eagerly, sessionDto.initialSessionState)

  override val isStopped: Boolean
    get() = sessionState.value.isStopped

  override val isPaused: Boolean
    get() = sessionState.value.isPaused

  val isReadOnly: Boolean
    get() = sessionState.value.isReadOnly

  val isPauseActionSupported: Boolean
    get() = sessionState.value.isPauseActionSupported

  val isSuspended: Boolean
    get() = sessionState.value.isSuspended

  override val editorsProvider: XDebuggerEditorsProvider = localEditorsProvider
                                                           ?: FrontendXDebuggerEditorsProvider(id, sessionDto.editorsProviderDto.fileTypeId)

  override val valueMarkers: XValueMarkers<FrontendXValue, XValueMarkerId> = FrontendXValueMarkers(project)

  private var _sessionTab: XDebugSessionTab? = null
  override val sessionTab: XDebugSessionTab?
    get() = _sessionTab

  // TODO all of the methods below
  // TODO pass in DTO?
  override val sessionName: String = sessionDto.sessionName
  override val sessionData: XDebugSessionData = createFeSessionData(sessionDto)

  override val restartActions: List<AnAction>
    get() = emptyList() // TODO
  override val extraActions: List<AnAction>
    get() = emptyList() // TODO
  override val extraStopActions: List<AnAction>
    get() = emptyList() // TODO
  override val processHandler: ProcessHandler = createProcessHandler(project, id, sessionDto.processHandlerDto)
  override val coroutineScope: CoroutineScope = cs
  override val currentStateMessage: String
    get() = if (isStopped) XDebuggerBundle.message("debugger.state.message.disconnected") else XDebuggerBundle.message("debugger.state.message.connected") // TODO
  override val currentStateHyperlinkListener: HyperlinkListener?
    get() = null // TODO

  init {
    cs.launch {
      sessionDto.sessionEvents.toFlow().collect { event ->
        when (event) {
          is XDebuggerSessionEvent.BeforeSessionResume -> eventsDispatcher.multicaster.beforeSessionResume()
          is XDebuggerSessionEvent.BreakpointsMuted -> eventsDispatcher.multicaster.breakpointsMuted(event.muted)
          is XDebuggerSessionEvent.SessionPaused -> eventsDispatcher.multicaster.sessionPaused()
          is XDebuggerSessionEvent.SessionResumed -> eventsDispatcher.multicaster.sessionResumed()
          is XDebuggerSessionEvent.SessionStopped -> eventsDispatcher.multicaster.sessionStopped()
          is XDebuggerSessionEvent.SettingsChanged -> eventsDispatcher.multicaster.settingsChanged()
          is XDebuggerSessionEvent.StackFrameChanged -> eventsDispatcher.multicaster.stackFrameChanged()
        }
      }
    }
    cs.launch {
      XDebugSessionApi.getInstance().sessionTabInfo(id).collectLatest { tabDto ->
        if (tabDto == null) return@collectLatest
        initTabInfo(tabDto)
        this.cancel() // Only one tab expected
      }
    }
  }

  private fun initTabInfo(tabDto: XDebuggerSessionTabDto) {
    val (tabInfo, pausedFlow) = tabDto
    cs.launch {
      if (tabInfo !is XDebuggerSessionTabInfo) return@launch

      val proxy = this@FrontendXDebuggerSession
      withContext(Dispatchers.EDT) {
        XDebugSessionTab.create(proxy, tabInfo.iconId?.icon(), tabInfo.executionEnvironment, tabInfo.contentToReuse,
                                tabInfo.forceNewDebuggerUi, tabInfo.withFramesCustomization).apply {
          _sessionTab = this
          proxy.onTabInitialized(this)
          showTab()
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

  override fun getFrameSourcePosition(frame: XStackFrame): XSourcePosition? {
    TODO("Not yet implemented")
  }

  override fun getCurrentExecutionStack(): XExecutionStack? {
    TODO("Not yet implemented")
  }

  override fun getCurrentStackFrame(): XStackFrame? {
    TODO("Not yet implemented")
  }

  override fun setCurrentStackFrame(executionStack: XExecutionStack, frame: XStackFrame, isTopFrame: Boolean) {
    TODO("Not yet implemented")
  }

  override fun hasSuspendContext(): Boolean {
    TODO("Not yet implemented")
  }

  override fun isSteppingSuspendContext(): Boolean {
    TODO("Not yet implemented")
  }

  override fun computeExecutionStacks(provideContainer: () -> XSuspendContext.XExecutionStackContainer) {
    TODO("Not yet implemented")
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
      XDebugSessionApi.getInstance().onTabInitialized(id, XDebuggerSessionTabInfoCallback(tab))
    }
  }

  override suspend fun sessionId(): XDebugSessionId = id

  fun closeScope() {
    cs.cancel()
  }

  companion object {
    private val LOG = thisLogger()

    suspend fun create(
      project: Project,
      scope: CoroutineScope,
      sessionDto: XDebugSessionDto,
    ): FrontendXDebuggerSession {
      val consoleView = sessionDto.consoleViewData?.consoleView()
      return FrontendXDebuggerSession(project, scope, sessionDto, consoleView)
    }
  }
}

// TODO pass breakpoints muted flow
private fun FrontendXDebuggerSession.createFeSessionData(sessionDto: XDebugSessionDto): XDebugSessionData =
  XDebugSessionData(project, sessionDto.sessionDataDto.configurationName)
