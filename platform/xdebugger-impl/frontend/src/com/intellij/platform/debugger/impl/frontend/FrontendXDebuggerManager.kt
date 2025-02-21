// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.util.messages.Topic
import com.intellij.xdebugger.impl.rpc.XDebuggerManagerApi
import com.intellij.xdebugger.impl.rpc.XDebuggerSessionEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Service(Service.Level.PROJECT)
internal class FrontendXDebuggerManager(private val project: Project, private val cs: CoroutineScope) {
  @OptIn(ExperimentalCoroutinesApi::class)
  val currentSession: StateFlow<FrontendXDebuggerSession?> =
    channelFlow<FrontendXDebuggerSession?> {
      XDebuggerManagerApi.getInstance().currentSession(project.projectId()).collectLatest { sessionDto ->
        if (sessionDto == null) {
          send(null)
          return@collectLatest
        }
        supervisorScope {
          val session = FrontendXDebuggerSession(project, this, sessionDto)
          send(session)
          awaitCancellation()
        }
      }
    }.stateIn(cs, SharingStarted.Eagerly, null)

  init {
    cs.launch {
      XDebuggerManagerApi.getInstance().sessionEvents(project.projectId()).collect { event ->
        when (event) {
          is XDebuggerSessionEvent.ProcessStarted -> {
            project.messageBus.syncPublisher(TOPIC).processStarted(event.sessionId, event.sessionDto)
          }
          is XDebuggerSessionEvent.ProcessStopped -> {
            project.messageBus.syncPublisher(TOPIC).processStopped(event.sessionId)
          }
          is XDebuggerSessionEvent.CurrentSessionChanged -> {
            project.messageBus.syncPublisher(TOPIC).activeSessionChanged(event.previousSession, event.currentSession)
          }
        }
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): FrontendXDebuggerManager = project.service()

    @Topic.ProjectLevel
    val TOPIC: Topic<FrontendXDebuggerManagerListener> =
      Topic("FrontendXDebuggerManager events", FrontendXDebuggerManagerListener::class.java)
  }
}