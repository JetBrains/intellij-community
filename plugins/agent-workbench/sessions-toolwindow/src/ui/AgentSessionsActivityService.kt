// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

// @spec community/plugins/agent-workbench/spec/agent-sessions-tree.spec.md

import com.intellij.agent.workbench.sessions.service.AgentSessionReadService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
internal class AgentSessionsActivityService(
  private val project: Project,
  scope: CoroutineScope,
) {
  private val _summary = MutableStateFlow(AgentSessionsActivitySummary.EMPTY)
  private val systemNotificationTracker = AgentSessionsSystemNotificationTracker()

  val summary: StateFlow<AgentSessionsActivitySummary> = _summary.asStateFlow()

  init {
    scope.launch(Dispatchers.Default) {
      service<AgentSessionReadService>().stateFlow().collect { state ->
        val nextSummary = buildAgentSessionsActivitySummary(state)
        _summary.value = nextSummary
        systemNotificationTracker
          .collectNotifications(nextSummary, isLoadedState = state.lastUpdatedAt != null)
          .forEach(::showAgentSessionsSystemNotification)
        project.service<AgentSessionsStripeIconUpdater>().scheduleUpdate()
      }
    }
  }

  fun latestSummary(): AgentSessionsActivitySummary = _summary.value
}
