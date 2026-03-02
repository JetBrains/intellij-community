// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.BaseAgentSessionSource
import com.intellij.openapi.project.Project

class ClaudeSessionSource(
  private val backend: ClaudeSessionBackend = createDefaultClaudeSessionBackend(),
) : BaseAgentSessionSource(provider = AgentSessionProvider.CLAUDE) {
  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    return backend.listThreads(path = path, openProject = openProject).map { it.toAgentSessionThread() }
  }
}

private fun ClaudeBackendThread.toAgentSessionThread(): AgentSessionThread {
  return AgentSessionThread(
    id = id,
    title = title,
    updatedAt = updatedAt,
    archived = false,
    provider = AgentSessionProvider.CLAUDE,
    originBranch = gitBranch,
  )
}
