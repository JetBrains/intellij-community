// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.actions

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction

internal class AgentSessionsTreePopupUnarchiveThreadAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?
  private val canUnarchiveProvider: (AgentSessionProvider) -> Boolean
  private val unarchiveThreads: (List<ArchiveThreadTarget>) -> Unit

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentSessionsTreePopupActionContext
    canUnarchiveProvider = { provider -> service<AgentSessionArchiveService>().canUnarchiveProvider(provider) }
    unarchiveThreads = { targets -> service<AgentSessionArchiveService>().unarchiveThreads(targets) }
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?,
    canUnarchiveProvider: (AgentSessionProvider) -> Boolean,
    unarchiveThreads: (List<ArchiveThreadTarget>) -> Unit,
  ) {
    this.resolveContext = resolveContext
    this.canUnarchiveProvider = canUnarchiveProvider
    this.unarchiveThreads = unarchiveThreads
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    if (context == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val unarchiveTargets = context.unarchiveTargets
    val canUnarchive = unarchiveTargets.any { target -> canUnarchiveProvider(target.provider) }
    e.presentation.isEnabledAndVisible = canUnarchive
    if (canUnarchive) {
      e.presentation.text = if (unarchiveTargets.size > 1) {
        AgentSessionsBundle.message("toolwindow.action.unarchive.selected.count", unarchiveTargets.size)
      }
      else {
        AgentSessionsBundle.message("toolwindow.action.unarchive")
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    if (context.unarchiveTargets.none { target -> canUnarchiveProvider(target.provider) }) {
      return
    }
    unarchiveThreads(context.unarchiveTargets)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
