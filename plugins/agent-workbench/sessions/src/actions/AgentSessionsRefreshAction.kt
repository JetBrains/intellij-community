// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.sessions.service.AgentSessionsService

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction

internal class AgentSessionsRefreshAction : DumbAwareAction {
  private val refreshSessions: () -> Unit
  private val isRefreshingProvider: () -> Boolean

  @Suppress("unused")
  constructor() {
    refreshSessions = { service<AgentSessionsService>().refresh() }
    isRefreshingProvider = { service<AgentSessionsService>().state.value.projects.any { it.isLoading } }
  }

  internal constructor(
    refreshSessions: () -> Unit,
    isRefreshingProvider: () -> Boolean,
  ) {
    this.refreshSessions = refreshSessions
    this.isRefreshingProvider = isRefreshingProvider
  }

  override fun actionPerformed(e: AnActionEvent) {
    refreshSessions()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = !isRefreshingProvider()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
