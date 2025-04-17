// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.impl.FrontendXDebuggerManagerListener
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.rpc.XDebugSessionDto
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XDebuggerManagerApi
import com.intellij.xdebugger.impl.rpc.XDebuggerManagerSessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
      XDebuggerManagerApi.getInstance().currentSession(project.projectId())
        .combine(sessionsFlow) { currentSessionId, sessions ->
          currentSessionId to sessions
        }
        .collectLatest { (currentSessionId, sessions) ->
          synchronousExecutor.trySend {
            this@channelFlow.send(sessions.firstOrNull { it.id == currentSessionId })
          }
        }
    }.stateIn(cs, SharingStarted.Eagerly, null)

  internal val breakpointsManager = FrontendXBreakpointManager(project, cs)
  internal val sessions get() = sessionsFlow.value

  init {
    cs.launch {
      for (event in synchronousExecutor) {
        event()
      }
    }

    cs.launch {
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
              project.messageBus.syncPublisher(FrontendXDebuggerManagerListener.TOPIC).sessionStarted(session)
            }
          }
          is XDebuggerManagerSessionEvent.ProcessStopped -> {
            synchronousExecutor.trySend {
              sessionsFlow.update { sessions ->
                val sessionToRemove = sessions.firstOrNull { it.id == event.sessionId }
                if (sessionToRemove != null) {
                  project.messageBus.syncPublisher(FrontendXDebuggerManagerListener.TOPIC).sessionStopped(sessionToRemove)
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
              project.messageBus.syncPublisher(FrontendXDebuggerManagerListener.TOPIC).activeSessionChanged(previousSession, currentSession)
            }
          }
        }
      }
    }
  }

  internal fun getSessionIdByContentDescriptor(descriptor: RunContentDescriptor): XDebugSessionId? {
    return sessionsFlow.value.firstOrNull { it.sessionTab?.runContentDescriptor === descriptor }?.id
  }

  private suspend fun createDebuggerSession(sessionDto: XDebugSessionDto): XDebugSessionProxy {
    val newSession = FrontendXDebuggerSession.create(project, cs, sessionDto)
    val old = sessionsFlow.getAndUpdate {
      it + newSession
    }
    assert(old.none { it.id == sessionDto.id }) { "Session with id ${sessionDto.id} already exists" }
    return newSession
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): FrontendXDebuggerManager = project.service()
  }
}
