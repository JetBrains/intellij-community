// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.impl.FrontendXDebuggerManagerListener
import com.intellij.xdebugger.impl.rpc.XDebugSessionDto
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XDebuggerManagerApi
import com.intellij.xdebugger.impl.rpc.XDebuggerManagerSessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
internal class FrontendXDebuggerManager(private val project: Project, private val cs: CoroutineScope) {
  private val sessions = MutableStateFlow<List<FrontendXDebuggerSession>>(listOf())
  private val synchronousExecutor = Channel<suspend () -> Unit>(capacity = Integer.MAX_VALUE)

  @OptIn(ExperimentalCoroutinesApi::class)
  val currentSession: StateFlow<FrontendXDebuggerSession?> =
    channelFlow {
      XDebuggerManagerApi.getInstance().currentSession(project.projectId())
        .combine(sessions) { currentSessionId, sessions ->
          currentSessionId to sessions
        }
        .collectLatest { (currentSessionId, sessions) ->
        synchronousExecutor.trySend {
          this@channelFlow.send(sessions.firstOrNull { it.id == currentSessionId })
        }
      }
    }.stateIn(cs, SharingStarted.Eagerly, null)

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
      project.messageBus.connect(cs).subscribe(FrontendXDebuggerManagerListener.TOPIC, object : FrontendXDebuggerManagerListener {
        override fun processStarted(sessionId: XDebugSessionId, sessionDto: XDebugSessionDto) {
          synchronousExecutor.trySend {
            createDebuggerSession(sessionDto)
          }
        }

        override fun processStopped(sessionId: XDebugSessionId) {
          synchronousExecutor.trySend {
            sessions.update { sessions ->
              val sessionToRemove = sessions.firstOrNull { it.id == sessionId }
              sessionToRemove?.closeScope()
              if (sessionToRemove != null) {
                sessions - sessionToRemove
              }
              else {
                sessions
              }
            }
          }
        }
      })
      eventFlow.toFlow().collect { event ->
        when (event) {
          is XDebuggerManagerSessionEvent.ProcessStarted -> {
            project.messageBus.syncPublisher(FrontendXDebuggerManagerListener.TOPIC).processStarted(event.sessionId, event.sessionDto)
          }
          is XDebuggerManagerSessionEvent.ProcessStopped -> {
            project.messageBus.syncPublisher(FrontendXDebuggerManagerListener.TOPIC).processStopped(event.sessionId)
          }
          is XDebuggerManagerSessionEvent.CurrentSessionChanged -> {
            project.messageBus.syncPublisher(FrontendXDebuggerManagerListener.TOPIC).activeSessionChanged(event.previousSession, event.currentSession)
          }
        }
      }
    }
  }

  private suspend fun createDebuggerSession(sessionDto: XDebugSessionDto) {
    val newSession = FrontendXDebuggerSession.create(project, cs, sessionDto)
    val previousSession = sessions.value.firstOrNull { it.id == sessionDto.id }
    sessions.getAndUpdate {
      it + newSession
    }
    previousSession?.closeScope()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): FrontendXDebuggerManager = project.service()
  }
}