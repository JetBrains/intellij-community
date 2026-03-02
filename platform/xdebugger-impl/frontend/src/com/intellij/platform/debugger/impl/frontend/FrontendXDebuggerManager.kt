// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManagerImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.platform.debugger.impl.frontend.editor.BreakpointPromoterEditorListener
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.XQuickEvaluateHandler
import com.intellij.platform.debugger.impl.frontend.frame.ImageEditorUIUtil
import com.intellij.platform.debugger.impl.rpc.XDebugSessionDto
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.platform.debugger.impl.rpc.XDebuggerManagerApi
import com.intellij.platform.debugger.impl.rpc.XDebuggerManagerSessionEvent
import com.intellij.platform.debugger.impl.rpc.XDebuggerValueLookupHintsRemoteApi
import com.intellij.platform.debugger.impl.rpc.XFrontendDebuggerCapabilities
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.platform.debugger.impl.ui.evaluate.quick.common.ValueLookupManager
import com.intellij.platform.project.projectId
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.asDisposable
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.impl.XDebuggerManagerProxyListener
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class FrontendXDebuggerManager(private val project: Project, private val cs: CoroutineScope) {
  private val sessionsFlow = MutableStateFlow<List<FrontendXDebuggerSession>>(listOf())

  /**
   * A session might be created after it's ID becomes the current one.
   * So we store the ID separately to update [_currentSessionFlow] when [sessionsFlow] updates.
   */
  private var currentSessionId: XDebugSessionId? = null
  private val _currentSessionFlow = MutableStateFlow<FrontendXDebuggerSession?>(null)

  val currentSessionFlow: StateFlow<FrontendXDebuggerSession?> = _currentSessionFlow.asStateFlow()
  val currentSession: FrontendXDebuggerSession? get() = _currentSessionFlow.value

  val breakpointsManager: FrontendXBreakpointManager = FrontendXBreakpointManager(project, cs)
  internal val sessions get() = sessionsFlow.value

  init {
    cs.launch {
      durable {
        initCapabilities()
      }
      initSessions()

      installEditorListeners()

      launch(Dispatchers.EDT) {
        // await listening started on the backend
        XDebuggerValueLookupHintsRemoteApi.getInstance().getValueLookupListeningFlow(project.projectId()).first { it }
        val lookupManager = ValueLookupManager.getInstance(project)
        lookupManager.startListening(XQuickEvaluateHandler())
        currentSessionFlow.collectLatest {
          lookupManager.hideHint()
        }
      }
    }
    if (SplitDebuggerMode.isSplitDebugger()) {
      startContentSelectionListening()
    }
  }

  private suspend fun initCapabilities() {
    XDebuggerManagerApi.getInstance().initialize(project.projectId(), XFrontendDebuggerCapabilities(
      canShowImages = ImageEditorUIUtil.canCreateImageEditor(),
    ))
  }

  private fun initSessions() = cs.launch {
    // When the registry flag is not set, we would prefer to have XDebugSessionProxy.Monolith in a listener
    // see com.intellij.xdebugger.impl.MonolithListenerAdapter
    val shouldTriggerListener = SplitDebuggerMode.isSplitDebugger()
    durableWithStateReset(block = {
      val (sessionsList, eventFlow) = XDebuggerManagerApi.getInstance().sessions(project.projectId())
      for (sessionDto in sessionsList) {
        createDebuggerSession(sessionDto)
      }
      eventFlow.toFlow().collect { event ->
        when (event) {
          is XDebuggerManagerSessionEvent.ProcessStarted -> {
            val session = createDebuggerSession(event.sessionDto)
            if (shouldTriggerListener) {
              project.messageBus.syncPublisher(XDebuggerManagerProxyListener.TOPIC).sessionStarted(session)
            }
          }
          is XDebuggerManagerSessionEvent.ProcessStopped -> {
            sessionsFlow.update { sessions ->
              val sessionToRemove = sessions.firstOrNull { it.id == event.sessionId }
              if (sessionToRemove != null) {
                if (shouldTriggerListener) {
                  project.messageBus.syncPublisher(XDebuggerManagerProxyListener.TOPIC).sessionStopped(sessionToRemove)
                }
                sessions - sessionToRemove
              }
              else {
                sessions
              }
            }
            updateCurrentSession()
          }
          is XDebuggerManagerSessionEvent.CurrentSessionChanged -> {
            val sessions = sessionsFlow.value
            val previousSession = findSessionById(sessions, event.previousSession)
            val currentSession = findSessionById(sessions, event.currentSession)
            currentSessionId = event.currentSession
            updateCurrentSession()
            if (shouldTriggerListener) {
              project.messageBus.syncPublisher(XDebuggerManagerProxyListener.TOPIC).activeSessionChanged(previousSession, currentSession)
            }
          }
        }
      }
    }, stateReset = {
      sessionsFlow.update { currentSessions ->
        if (shouldTriggerListener) {
          for (session in currentSessions) {
            project.messageBus.syncPublisher(XDebuggerManagerProxyListener.TOPIC).sessionStopped(session)
          }
        }
        listOf()
      }
      updateCurrentSession()
    })
  }

  private fun updateCurrentSession() {
    _currentSessionFlow.value = findSessionById(sessions, currentSessionId)
  }

  private fun findSessionById(sessions: List<FrontendXDebuggerSession>, sessionId: XDebugSessionId?): FrontendXDebuggerSession? =
    sessions.firstOrNull { it.id == sessionId }

  private fun installEditorListeners() {
    val eventMulticaster = EditorFactory.getInstance().getEventMulticaster()
    val bpPromoter = BreakpointPromoterEditorListener(project, cs)
    eventMulticaster.addEditorMouseMotionListener(bpPromoter, cs.asDisposable())
    eventMulticaster.addEditorMouseListener(bpPromoter, cs.asDisposable())
  }

  private fun getSessionIdByContentDescriptor(descriptor: RunContentDescriptor): XDebugSessionId? {
    return sessions.firstOrNull { it.sessionTab?.runContentDescriptor === descriptor }?.id
  }

  private fun createDebuggerSession(sessionDto: XDebugSessionDto): XDebugSessionProxy {
    val newSession = FrontendXDebuggerSession(project, cs, this, sessionDto)
    val old = sessionsFlow.getAndUpdate {
      it + newSession
    }
    updateCurrentSession()
    assert(old.none { it.id == sessionDto.id }) { "Session with id ${sessionDto.id} already exists" }
    return newSession
  }

  private fun startContentSelectionListening() {
    val selectedSessionId = MutableSharedFlow<XDebugSessionId?>(1, 1, BufferOverflow.DROP_OLDEST)
    cs.launch {
      selectedSessionId.collectLatest { sessionId ->
        XDebuggerManagerApi.getInstance().sessionTabSelected(project.projectId(), sessionId)
      }
    }

    val contentListener = object : ContentManagerListener {
      override fun selectionChanged(event: ContentManagerEvent) {
        val descriptor = getDescriptor(event) ?: return
        val sessionId = getSessionIdByContentDescriptor(descriptor)
        selectedSessionId.tryEmit(sessionId)
      }

      override fun contentRemoved(event: ContentManagerEvent) {
        val descriptor = getDescriptor(event) ?: return
        val sessionId = getSessionIdByContentDescriptor(descriptor) ?: return
        cs.launch {
          XDebuggerManagerApi.getInstance().sessionTabClosed(sessionId)
        }
      }

      private fun getDescriptor(event: ContentManagerEvent): RunContentDescriptor? {
        if (event.operation != ContentManagerEvent.ContentOperation.add) return null
        val executor = RunContentManagerImpl.getExecutorByContent(event.content) ?: return null
        if (executor.toolWindowId != ToolWindowId.DEBUG) return null
        return RunContentManagerImpl.getRunContentDescriptorByContent(event.content)
      }
    }

    project.messageBus.connect().subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun toolWindowsRegistered(ids: List<String?>, toolWindowManager: ToolWindowManager) {
        for (id in ids) {
          val toolWindow = toolWindowManager.getToolWindow(id) ?: continue
          toolWindow.addContentManagerListener(contentListener)
        }
      }
    })
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): FrontendXDebuggerManager = project.service()
  }
}
