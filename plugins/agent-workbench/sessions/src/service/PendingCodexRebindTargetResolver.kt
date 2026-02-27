// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindTarget
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.agent.workbench.sessions.util.buildAgentSessionResumeCommand
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceIfCreated

@Service(Service.Level.APP)
internal class PendingCodexRebindTargetResolver(
  private val stateProvider: () -> AgentSessionsState? = {
    ApplicationManager.getApplication().serviceIfCreated<AgentSessionsService>()?.state?.value
  },
  private val resumeCommandProvider: (AgentSessionProvider, String) -> List<String> = ::buildAgentSessionResumeCommand,
) {
  fun resolve(context: AgentChatEditorTabActionContext): AgentChatPendingTabRebindTarget? {
    if (!isPendingCodexEditorContext(context)) {
      return null
    }

    val state = stateProvider() ?: return null
    val candidateThreads = resolveThreadsForPath(state, context.path)
      .asSequence()
      .filter { thread -> thread.provider == AgentSessionProvider.CODEX }
      .sortedByDescending { thread -> thread.updatedAt }
      .toList()

    for (thread in candidateThreads) {
      val threadIdentity = buildAgentSessionIdentity(provider = thread.provider, sessionId = thread.id)
      val shellCommand = runCatching {
        resumeCommandProvider(thread.provider, thread.id)
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
}

internal fun isPendingCodexEditorContext(context: AgentChatEditorTabActionContext): Boolean {
  return context.isPendingThread && context.provider == AgentSessionProvider.CODEX
}
