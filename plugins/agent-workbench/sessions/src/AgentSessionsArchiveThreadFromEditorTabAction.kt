// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction

internal class AgentSessionsArchiveThreadFromEditorTabAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentChatThreadEditorTabActionContext?
  private val canArchiveThread: (AgentSessionThread) -> Boolean
  private val archiveThread: (String, AgentSessionThread) -> Unit

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentChatThreadEditorTabActionContext
    canArchiveThread = { thread -> service<AgentSessionsService>().canArchiveThread(thread) }
    archiveThread = { path, thread -> service<AgentSessionsService>().archiveThread(path, thread) }
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentChatThreadEditorTabActionContext?,
    canArchiveThread: (AgentSessionThread) -> Boolean,
    archiveThread: (String, AgentSessionThread) -> Unit,
  ) {
    this.resolveContext = resolveContext
    this.canArchiveThread = canArchiveThread
    this.archiveThread = archiveThread
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    if (!canArchiveThread(context.thread)) {
      return
    }
    archiveThread(context.path, context.thread)
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    if (context == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isVisible = true
    e.presentation.isEnabled = canArchiveThread(context.thread)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
