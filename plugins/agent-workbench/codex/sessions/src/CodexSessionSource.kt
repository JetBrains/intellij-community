// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

// @spec community/plugins/agent-workbench/spec/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-sessions-codex-rollout-source.spec.md

import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivity
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.backend.createDefaultCodexSessionBackend
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.providers.BaseAgentSessionSource
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow

class CodexSessionSource(
  private val backend: CodexSessionBackend = createDefaultCodexSessionBackend(),
) : BaseAgentSessionSource(provider = AgentSessionProvider.CODEX, canReportExactThreadCount = false) {
  override val supportsUpdates: Boolean
    get() = true

  override val updates: Flow<Unit>
    get() = backend.updates

  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    return backend.listThreads(path = path, openProject = openProject).map { it.toAgentSessionThread() }
  }

  override suspend fun prefetchThreads(paths: List<String>): Map<String, List<AgentSessionThread>> {
    val prefetched = backend.prefetchThreads(paths)
    if (prefetched.isEmpty()) return emptyMap()
    return prefetched.mapValues { (_, threads) ->
      threads.map { it.toAgentSessionThread() }
    }
  }
}

private fun CodexBackendThread.toAgentSessionThread(): AgentSessionThread {
  return thread.toAgentSessionThread(activity = activity)
}

private fun CodexThread.toAgentSessionThread(activity: CodexSessionActivity): AgentSessionThread {
  return AgentSessionThread(
    id = id,
    title = title,
    updatedAt = updatedAt,
    archived = archived,
    provider = AgentSessionProvider.CODEX,
    subAgents = subAgents.map { AgentSubAgent(it.id, it.name) },
    originBranch = gitBranch,
    activity = activity.toAgentThreadActivity(),
  )
}

private fun CodexSessionActivity.toAgentThreadActivity(): AgentThreadActivity {
  return when (this) {
    CodexSessionActivity.UNREAD -> AgentThreadActivity.UNREAD
    CodexSessionActivity.REVIEWING -> AgentThreadActivity.REVIEWING
    CodexSessionActivity.PROCESSING -> AgentThreadActivity.PROCESSING
    CodexSessionActivity.READY -> AgentThreadActivity.READY
  }
}
