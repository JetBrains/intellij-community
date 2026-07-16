// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.execution.Executor
import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.execution.ui.RunContentWithExecutorListener
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
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
import com.intellij.util.asDisposable
import com.intellij.xdebugger.impl.XDebuggerManagerProxyListener
import fleet.rpc.client.RpcClientDisconnectedException
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
        durable {
          XDebuggerValueLookupHintsRemoteApi.getInstance().getValueLookupListeningFlow(project.projectId()).first { it }
        }
        val lookupManager = ValueLookupManager.getInstance(project)
        lookupManager.startListening(XQuickEvaluateHandler())
        currentSessionFlow.collectLatest {
          lookupManager.hideHint()
        }
      }
    }
    startContentSelectionListening()
  }

  private suspend fun initCapabilities() {
    XDebuggerManagerApi.getInstance().initialize(project.projectId(), XFrontendDebuggerCapabilities(
      canShowImages = ImageEditorUIUtil.canCreateImageEditor(),
    ))
  }

  private fun initSessions() = cs.launch {
    durableWithStateReset(block = {
      val (sessionsList, eventFlow) = XDebuggerManagerApi.getInstance().sessions(project.projectId())
      for (sessionDto in sessionsList) {
        createDebuggerSession(sessionDto)
      }
      eventFlow.toFlow().collect { event ->
        when (event) {
          is XDebuggerManagerSessionEvent.ProcessStarted -> {
            val session = createDebuggerSession(event.sessionDto)
            project.messageBus.syncPublisher(XDebuggerManagerProxyListener.TOPIC).sessionStarted(session)
          }
          is XDebuggerManagerSessionEvent.ProcessStopped -> {
            sessionsFlow.update { sessions ->
              val sessionToRemove = sessions.firstOrNull { it.id == event.sessionId }
              if (sessionToRemove != null) {
                project.messageBus.syncPublisher(XDebuggerManagerProxyListener.TOPIC).sessionStopped(sessionToRemove)
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
            project.messageBus.syncPublisher(XDebuggerManagerProxyListener.TOPIC).activeSessionChanged(previousSession, currentSession)
          }
        }
      }
    }, stateReset = {
      sessionsFlow.update { currentSessions ->
        for (session in currentSessions) {
          project.messageBus.syncPublisher(XDebuggerManagerProxyListener.TOPIC).sessionStopped(session)
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
        try {
          XDebuggerManagerApi.getInstance().sessionTabSelected(project.projectId(), sessionId)
        }
        catch (_: RpcClientDisconnectedException) {
          // Content selection can race with remote debugger disconnect.
        }
      }
    }

    project.messageBus.connect(cs).subscribe(RunContentManager.TOPIC, object : RunContentWithExecutorListener {
      override fun contentSelected(descriptor: RunContentDescriptor?, executor: Executor) {
        if (executor.toolWindowId != ToolWindowId.DEBUG) return
        if (descriptor == null) return
        val sessionId = getSessionIdByContentDescriptor(descriptor)
        selectedSessionId.tryEmit(sessionId)
      }

      override fun contentRemoved(descriptor: RunContentDescriptor?, executor: Executor) {
        if (executor.toolWindowId != ToolWindowId.DEBUG) return
        if (descriptor == null) return
        val descriptorId = descriptor.id as? RunContentDescriptorIdImpl ?: return
        cs.launch {
          try {
            XDebuggerManagerApi.getInstance().sessionTabClosed(descriptorId)
          }
          catch (_: RpcClientDisconnectedException) {
            // The backend may already be disconnected, so there is no tab to close remotely.
          }
        }
      }
    })
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): FrontendXDebuggerManager = project.service()
  }
}
