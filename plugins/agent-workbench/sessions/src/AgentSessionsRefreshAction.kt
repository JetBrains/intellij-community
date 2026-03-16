// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction

internal class AgentSessionsRefreshAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    service<AgentSessionsService>().refresh()
  }

  override fun update(e: AnActionEvent) {
    val isRefreshing = service<AgentSessionsService>().state.value.projects.any { it.isLoading }
    e.presentation.isEnabled = !isRefreshing
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
