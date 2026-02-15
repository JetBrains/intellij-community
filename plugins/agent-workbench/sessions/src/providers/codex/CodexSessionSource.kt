// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.providers.codex

// @spec community/plugins/agent-workbench/spec/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-sessions-codex-rollout-source.spec.md

import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.sessions.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.CodexSessionActivity
import com.intellij.agent.workbench.codex.sessions.CodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.createDefaultCodexSessionBackend
import com.intellij.agent.workbench.sessions.AgentSessionActivity
import com.intellij.agent.workbench.sessions.AgentSessionProvider
import com.intellij.agent.workbench.sessions.AgentSessionThread
import com.intellij.agent.workbench.sessions.AgentSubAgent
import com.intellij.agent.workbench.sessions.providers.BaseAgentSessionSource
import com.intellij.openapi.project.Project

internal class CodexSessionSource(
  private val backend: CodexSessionBackend = createDefaultCodexSessionBackend(),
) : BaseAgentSessionSource(provider = AgentSessionProvider.CODEX, canReportExactThreadCount = false) {
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
    activity = activity.toAgentSessionActivity(),
  )
}

private fun CodexSessionActivity.toAgentSessionActivity(): AgentSessionActivity {
  return when (this) {
    CodexSessionActivity.UNREAD -> AgentSessionActivity.UNREAD
    CodexSessionActivity.REVIEWING -> AgentSessionActivity.REVIEWING
    CodexSessionActivity.PROCESSING -> AgentSessionActivity.PROCESSING
    CodexSessionActivity.READY -> AgentSessionActivity.READY
  }
}
