// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.util.messages.Topic
import com.intellij.xdebugger.impl.rpc.XDebugSessionDto
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XDebuggerManagerApi
import com.intellij.xdebugger.impl.rpc.XDebuggerSessionEvent
import fleet.multiplatform.shims.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

@Service(Service.Level.PROJECT)
internal class FrontendXDebuggerManager(private val project: Project, private val cs: CoroutineScope) {
  private val sessions = Collections.synchronizedMap(LinkedHashMap<XDebugSessionId, FrontendXDebuggerSession>())

  @OptIn(ExperimentalCoroutinesApi::class)
  val currentSession: StateFlow<FrontendXDebuggerSession?> =
    channelFlow {
      XDebuggerManagerApi.getInstance().currentSession(project.projectId()).collectLatest { sessionId ->
        send(sessions[sessionId])
      }
    }.stateIn(cs, SharingStarted.Eagerly, null)

  init {
    cs.launch {
      val (sessionsList, eventFlow) = XDebuggerManagerApi.getInstance().sessions(project.projectId())
      for (sessionDto in sessionsList) {
        createDebuggerSession(sessionDto)
      }
      project.messageBus.connect(cs).subscribe(TOPIC, object : FrontendXDebuggerManagerListener {
        override fun processStarted(sessionId: XDebugSessionId, sessionDto: XDebugSessionDto) {
          createDebuggerSession(sessionDto)
        }

        override fun processStopped(sessionId: XDebugSessionId) {
          sessions.remove(sessionId)?.closeScope()
        }
      })
      eventFlow.toFlow().collect { event ->
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

  private fun createDebuggerSession(sessionDto: XDebugSessionDto) {
    val frontendSession = FrontendXDebuggerSession(project, cs, sessionDto)
    val previousSession = sessions.put(sessionDto.id, frontendSession)
    previousSession?.closeScope()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): FrontendXDebuggerManager = project.service()

    @Topic.ProjectLevel
    val TOPIC: Topic<FrontendXDebuggerManagerListener> =
      Topic("FrontendXDebuggerManager events", FrontendXDebuggerManagerListener::class.java)
  }
}