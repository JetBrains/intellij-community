// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.service.normalizeOpenableSourceProjectPath

internal fun AgentSessionsState.filterToCurrentProjectSessions(currentProjectPath: String?): AgentSessionsState {
  val normalizedCurrentProjectPath = currentProjectPath?.let(::normalizeOpenableSourceProjectPath) ?: return this
  val filteredProjects = projects.mapNotNull { project -> project.filterToCurrentProjectSessions(normalizedCurrentProjectPath) }
  return copy(projects = filteredProjects)
}

internal fun List<AgentSessionsActivityThreadRow>.filterToCurrentProjectActivityRows(currentProjectPath: String?): List<AgentSessionsActivityThreadRow> {
  val normalizedCurrentProjectPath = currentProjectPath?.let(::normalizeOpenableSourceProjectPath) ?: return this
  return filter { row -> normalizeAgentWorkbenchPath(row.path) == normalizedCurrentProjectPath }
}

private fun AgentProjectSessions.filterToCurrentProjectSessions(normalizedCurrentProjectPath: String): AgentProjectSessions? {
  if (normalizeAgentWorkbenchPath(path) == normalizedCurrentProjectPath) {
    return copy(worktrees = emptyList())
  }

  val matchingWorktree = worktrees.firstOrNull { worktree -> normalizeAgentWorkbenchPath(worktree.path) == normalizedCurrentProjectPath }
                         ?: return null
  if (!matchingWorktree.hasVisibleContent()) {
    return null
  }
  return copy(
    threads = emptyList(),
    errorMessage = null,
    providerWarnings = emptyList(),
    providerLoadStates = emptyMap(),
    providersWithUnknownThreadCount = emptySet(),
    worktrees = listOf(matchingWorktree),
  )
}

private fun AgentWorktree.hasVisibleContent(): Boolean {
  return threads.isNotEmpty() || isLoading || errorMessage != null || providerWarnings.isNotEmpty()
}
