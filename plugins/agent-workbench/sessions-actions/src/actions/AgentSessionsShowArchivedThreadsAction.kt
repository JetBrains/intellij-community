// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.sessions.model.AgentSessionThreadViewMode
import com.intellij.agent.workbench.sessions.service.AgentArchivedSessionsService
import com.intellij.agent.workbench.sessions.state.AgentSessionThreadViewStateService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction

internal class AgentSessionsShowArchivedThreadsAction : DumbAwareAction {
  private val viewMode: () -> AgentSessionThreadViewMode
  private val setViewMode: (AgentSessionThreadViewMode) -> Unit
  private val ensureArchivedSessionsLoaded: () -> Unit

  @Suppress("unused")
  constructor() {
    viewMode = { service<AgentSessionThreadViewStateService>().state.value.mode }
    setViewMode = { mode -> service<AgentSessionThreadViewStateService>().setMode(mode) }
    ensureArchivedSessionsLoaded = { service<AgentArchivedSessionsService>().ensureLoaded() }
  }

  internal constructor(
    viewMode: () -> AgentSessionThreadViewMode,
    setViewMode: (AgentSessionThreadViewMode) -> Unit,
    ensureArchivedSessionsLoaded: () -> Unit,
  ) {
    this.viewMode = viewMode
    this.setViewMode = setViewMode
    this.ensureArchivedSessionsLoaded = ensureArchivedSessionsLoaded
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = viewMode() != AgentSessionThreadViewMode.ARCHIVED
  }

  override fun actionPerformed(e: AnActionEvent) {
    setViewMode(AgentSessionThreadViewMode.ARCHIVED)
    ensureArchivedSessionsLoaded()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
