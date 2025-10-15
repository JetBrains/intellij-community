// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.common.ValueLookupManager
import com.intellij.platform.debugger.impl.frontend.frame.ImageEditorUIUtil
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.platform.project.projectId
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.asDisposable
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.impl.XDebuggerManagerProxyListener
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class FrontendXDebuggerManager(private val project: Project, private val cs: CoroutineScope) {
  private val sessionsFlow = MutableStateFlow<List<FrontendXDebuggerSession>>(listOf())
  private val synchronousExecutor = Channel<suspend () -> Unit>(capacity = Integer.MAX_VALUE)

  @OptIn(ExperimentalCoroutinesApi::class)
  val currentSession: StateFlow<FrontendXDebuggerSession?> =
    channelFlow {
      durableWithStateReset(block = {
        val currentSessionFlow = XDebuggerManagerApi.getInstance().currentSession(project.projectId())
        currentSessionFlow
          .combine(sessionsFlow) { currentSessionId, sessions ->
            currentSessionId to sessions
          }
          .collectLatest { (currentSessionId, sessions) ->
            synchronousExecutor.trySend {
              this@channelFlow.send(sessions.firstOrNull { it.id == currentSessionId })
            }
          }
      }, stateReset = {
        synchronousExecutor.trySend { this@channelFlow.send(null) }
      })
    }.stateIn(cs, SharingStarted.Eagerly, null)

  val breakpointsManager: FrontendXBreakpointManager = FrontendXBreakpointManager(project, cs)
  internal val sessions get() = sessionsFlow.value

  init {
    initCapabilities()
    // TODO: make sure that capabilities are send before anything else

    cs.launch {
      for (event in synchronousExecutor) {
        event()
      }
    }

    initSessions()

    installEditorListeners()

    cs.launch(Dispatchers.EDT) {
      // await listening started on the backend
      XDebuggerValueLookupHintsRemoteApi.getInstance().getValueLookupListeningFlow(project.projectId()).filter { it }.first()
      ValueLookupManager.getInstance(project).startListening()
    }
  }

  private fun initCapabilities() = cs.launch {
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
        synchronousExecutor.trySend {
          createDebuggerSession(sessionDto)
        }
      }
      eventFlow.toFlow().collect { event ->
        when (event) {
          is XDebuggerManagerSessionEvent.ProcessStarted -> {
            synchronousExecutor.trySend {
              val session = createDebuggerSession(event.sessionDto)
              if (shouldTriggerListener) {
                project.messageBus.syncPublisher(XDebuggerManagerProxyListener.TOPIC).sessionStarted(session)
              }
            }
          }
          is XDebuggerManagerSessionEvent.ProcessStopped -> {
            synchronousExecutor.trySend {
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
            }
          }
          is XDebuggerManagerSessionEvent.CurrentSessionChanged -> {
            synchronousExecutor.trySend {
              val sessions = sessionsFlow.value
              val previousSession = sessions.firstOrNull { it.id == event.previousSession }
              val currentSession = sessions.firstOrNull { it.id == event.currentSession }
              if (shouldTriggerListener) {
                project.messageBus.syncPublisher(XDebuggerManagerProxyListener.TOPIC).activeSessionChanged(previousSession, currentSession)
              }
            }
          }
        }
      }
    }, stateReset = {
      synchronousExecutor.trySend {
        sessionsFlow.update { currentSessions ->
          if (shouldTriggerListener) {
            for (session in currentSessions) {
              project.messageBus.syncPublisher(XDebuggerManagerProxyListener.TOPIC).sessionStopped(session)
            }
          }
          listOf()
        }
      }
    })
  }

  private fun installEditorListeners() {
    val eventMulticaster = EditorFactory.getInstance().getEventMulticaster()
    val bpPromoter = BreakpointPromoterEditorListener(project, cs)
    eventMulticaster.addEditorMouseMotionListener(bpPromoter, cs.asDisposable())
  }

  private fun getSessionIdByContentDescriptor(descriptor: RunContentDescriptor): XDebugSessionId? {
    return sessions.firstOrNull { it.sessionTab?.runContentDescriptor === descriptor }?.id
  }

  private suspend fun createDebuggerSession(sessionDto: XDebugSessionDto): XDebugSessionProxy {
    val newSession = FrontendXDebuggerSession.create(project, cs, this, sessionDto)
    val old = sessionsFlow.getAndUpdate {
      it + newSession
    }
    assert(old.none { it.id == sessionDto.id }) { "Session with id ${sessionDto.id} already exists" }
    return newSession
  }

  internal fun startContentSelectionListening() {
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
