// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindTarget
import com.intellij.agent.workbench.chat.rebindSpecificOpenPendingCodexTab
import com.intellij.agent.workbench.chat.resolveAgentChatEditorTabActionContext
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.DumbAwareAction

internal class AgentSessionsBindPendingCodexThreadFromEditorTabAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext?
  private val resolveTarget: (AgentChatEditorTabActionContext) -> AgentChatPendingTabRebindTarget?
  private val rebindPendingTab: (String, String, AgentChatPendingTabRebindTarget) -> Boolean

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentChatEditorTabActionContext
    resolveTarget = ::resolvePendingCodexRebindTarget
    rebindPendingTab = ::rebindSpecificOpenPendingCodexTab
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext?,
    resolveTarget: (AgentChatEditorTabActionContext) -> AgentChatPendingTabRebindTarget?,
    rebindPendingTab: (String, String, AgentChatPendingTabRebindTarget) -> Boolean,
  ) {
    this.resolveContext = resolveContext
    this.resolveTarget = resolveTarget
    this.rebindPendingTab = rebindPendingTab
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    if (!isPendingCodexEditorContext(context)) {
      return
    }
    val target = resolveTarget(context) ?: return
    rebindPendingTab(context.path, context.threadIdentity, target)
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    if (context == null || !isPendingCodexEditorContext(context)) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isVisible = true
    e.presentation.isEnabled = resolveTarget(context) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private fun isPendingCodexEditorContext(context: AgentChatEditorTabActionContext): Boolean {
  return context.isPendingThread && context.provider == AgentSessionProvider.CODEX
}

private fun resolvePendingCodexRebindTarget(context: AgentChatEditorTabActionContext): AgentChatPendingTabRebindTarget? {
  if (!isPendingCodexEditorContext(context)) {
    return null
  }

  val sessionsService = ApplicationManager.getApplication().serviceIfCreated<AgentSessionsService>() ?: return null
  val sessionsState = sessionsService.state.value
  val candidateThreads = resolveThreadsForPath(sessionsState, context.path)
    .asSequence()
    .filter { thread -> thread.provider == AgentSessionProvider.CODEX }
    .sortedByDescending { thread -> thread.updatedAt }
    .toList()

  for (thread in candidateThreads) {
    val threadIdentity = buildAgentSessionIdentity(provider = thread.provider, sessionId = thread.id)
    val shellCommand = runCatching {
      buildAgentSessionResumeCommand(provider = thread.provider, sessionId = thread.id)
    }.getOrDefault(listOf(AgentSessionProvider.CODEX.value, "resume", thread.id))
    return AgentChatPendingTabRebindTarget(
      threadIdentity = threadIdentity,
      threadId = thread.id,
      shellCommand = shellCommand,
      threadTitle = thread.title,
      threadActivity = thread.activity,
      threadUpdatedAt = thread.updatedAt,
    )
  }

  return null
}

private fun resolveThreadsForPath(state: AgentSessionsState, path: String): List<AgentSessionThread> {
  val normalizedPath = normalizeAgentWorkbenchPath(path)
  val projectThreads = state.projects.firstOrNull { project -> project.path == normalizedPath }?.threads
  if (projectThreads != null) {
    return projectThreads
  }

  for (project in state.projects) {
    val worktreeThreads = project.worktrees.firstOrNull { worktree -> worktree.path == normalizedPath }?.threads
    if (worktreeThreads != null) {
      return worktreeThreads
    }
  }

  return emptyList()
}
