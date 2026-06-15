// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-tree.spec.md

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Service(Service.Level.APP)
internal class AgentSessionsSystemNotificationService(
  scope: CoroutineScope,
) {
  private val tracker = AgentSessionsSystemNotificationTracker()

  init {
    scope.launch(Dispatchers.Default) {
      serviceAsync<AgentSessionsActivityModel>().snapshot.collect { snapshot ->
        val notifications = tracker.collectNotifications(
          summary = snapshot.rawSummary,
          isLoadedState = snapshot.isLoadedState,
        )
        if (shouldShowAgentSessionsSystemNotifications()) {
          notifications.forEach { notification -> showAgentSessionsSystemNotification(notification) }
        }
      }
    }
  }
}

internal fun shouldShowAgentSessionsSystemNotifications(
  application: Application = ApplicationManager.getApplication(),
): Boolean {
  return !application.isActive && !application.isUnitTestMode
}
