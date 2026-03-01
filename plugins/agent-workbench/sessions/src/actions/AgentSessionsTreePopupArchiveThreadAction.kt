// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.service.AgentSessionsService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction

internal class AgentSessionsTreePopupArchiveThreadAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?
  private val canArchiveProvider: (AgentSessionProvider) -> Boolean
  private val archiveThreads: (List<ArchiveThreadTarget>) -> Unit

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentSessionsTreePopupActionContext
    canArchiveProvider = { provider -> service<AgentSessionsService>().canArchiveProvider(provider) }
    archiveThreads = { targets -> service<AgentSessionsService>().archiveThreads(targets) }
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?,
    canArchiveProvider: (AgentSessionProvider) -> Boolean,
    archiveThreads: (List<ArchiveThreadTarget>) -> Unit,
  ) {
    this.resolveContext = resolveContext
    this.canArchiveProvider = canArchiveProvider
    this.archiveThreads = archiveThreads
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    if (context == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val archiveTargets = context.archiveTargets
    val canArchive = archiveTargets.any { target -> canArchiveProvider(target.provider) }
    e.presentation.isEnabledAndVisible = canArchive
    if (canArchive) {
      e.presentation.text = if (archiveTargets.size > 1) {
        AgentSessionsBundle.message("toolwindow.action.archive.selected.count", archiveTargets.size)
      }
      else {
        AgentSessionsBundle.message("toolwindow.action.archive")
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    if (context.archiveTargets.none { target -> canArchiveProvider(target.provider) }) {
      return
    }
    archiveThreads(context.archiveTargets)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
