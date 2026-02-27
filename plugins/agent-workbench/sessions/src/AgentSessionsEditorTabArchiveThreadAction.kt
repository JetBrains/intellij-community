// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.resolveAgentChatEditorTabActionContext
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction

internal class AgentSessionsEditorTabArchiveThreadAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext?
  private val canArchiveProvider: (AgentSessionProvider) -> Boolean
  private val archiveThreads: (List<ArchiveThreadTarget>) -> Unit

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentChatEditorTabActionContext
    canArchiveProvider = { provider -> service<AgentSessionsService>().canArchiveProvider(provider) }
    archiveThreads = { targets -> service<AgentSessionsService>().archiveThreads(targets) }
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext?,
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

    val archiveTarget = resolveArchiveThreadTargetFromEditorTabContext(context)
    e.presentation.text = templatePresentation.textWithMnemonic
    e.presentation.isVisible = true
    e.presentation.isEnabled = archiveTarget != null && canArchiveProvider(archiveTarget.provider)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val archiveTarget = resolveArchiveThreadTargetFromEditorTabContext(context) ?: return
    if (!canArchiveProvider(archiveTarget.provider)) {
      return
    }
    archiveThreads(listOf(archiveTarget))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
