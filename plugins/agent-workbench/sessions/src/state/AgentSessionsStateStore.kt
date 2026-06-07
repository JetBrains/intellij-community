// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.state

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.service.failLoadingProviderLoadMetadata
import com.intellij.openapi.components.Service
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Service(Service.Level.APP)
class AgentSessionsStateStore {
  private val mutableState = MutableStateFlow(AgentSessionsState())
  val state: StateFlow<AgentSessionsState> = mutableState.asStateFlow()

  fun snapshot(): AgentSessionsState = mutableState.value

  fun update(transform: (AgentSessionsState) -> AgentSessionsState) {
    mutableState.update(transform)
  }

  fun replaceProjects(projects: List<AgentProjectSessions>, visibleThreadCounts: Map<String, Int>) {
    update {
      it.copy(
        projects = projects,
        visibleThreadCounts = visibleThreadCounts,
        lastUpdatedAt = System.currentTimeMillis(),
      )
    }
  }

  fun markLoadFailure(errorMessage: String) {
    update { state ->
      state.copy(
        projects = state.projects.map { project ->
          val projectLoadMetadata = failLoadingProviderLoadMetadata(
            providerLoadStates = project.providerLoadStates,
            providersWithUnknownThreadCount = project.providersWithUnknownThreadCount,
          )
          project.copy(
            errorMessage = errorMessage,
            providerWarnings = emptyList(),
            providerLoadStates = projectLoadMetadata.providerLoadStates,
            providersWithUnknownThreadCount = projectLoadMetadata.providersWithUnknownThreadCount,
            worktrees = project.worktrees.map { wt ->
              val worktreeLoadMetadata = failLoadingProviderLoadMetadata(
                providerLoadStates = wt.providerLoadStates,
                providersWithUnknownThreadCount = wt.providersWithUnknownThreadCount,
              )
              wt.copy(
                providerWarnings = emptyList(),
                providerLoadStates = worktreeLoadMetadata.providerLoadStates,
                providersWithUnknownThreadCount = worktreeLoadMetadata.providersWithUnknownThreadCount,
              )
            },
          )
        },
        lastUpdatedAt = System.currentTimeMillis(),
      )
    }
  }

  fun showMoreProjects() {
    update {
      it.copy(
        visibleClosedProjectCount = it.visibleClosedProjectCount + DEFAULT_VISIBLE_CLOSED_PROJECT_COUNT,
      )
    }
  }

  fun ensureProjectVisible(path: String) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    update { state ->
      val requiredClosedProjectCount = requiredVisibleClosedProjectCount(state.projects, normalizedPath) ?: return@update state
      if (requiredClosedProjectCount <= state.visibleClosedProjectCount) {
        state
      }
      else {
        state.copy(visibleClosedProjectCount = requiredClosedProjectCount)
      }
    }
  }

  fun showMoreThreads(path: String) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    update { state ->
      val current = state.visibleThreadCounts[normalizedPath] ?: DEFAULT_VISIBLE_THREAD_COUNT
      val nextVisible = current + DEFAULT_VISIBLE_THREAD_COUNT
      state.copy(visibleThreadCounts = state.visibleThreadCounts + (normalizedPath to nextVisible))
    }
  }

  fun ensureThreadVisible(path: String, provider: AgentSessionProvider, threadId: String) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    update { state ->
      val threadIndex = findThreadIndex(
        projects = state.projects,
        normalizedPath = normalizedPath,
        provider = provider,
        threadId = threadId,
      ) ?: return@update state
      val currentVisible = state.visibleThreadCounts[normalizedPath] ?: DEFAULT_VISIBLE_THREAD_COUNT
      if (threadIndex < currentVisible) {
        return@update state
      }
      val minVisible = threadIndex + 1
      var nextVisible = currentVisible
      while (nextVisible < minVisible) {
        nextVisible += DEFAULT_VISIBLE_THREAD_COUNT
      }
      state.copy(visibleThreadCounts = state.visibleThreadCounts + (normalizedPath to nextVisible))
    }
  }

  fun buildInitialVisibleThreadCounts(knownPaths: List<String>): Map<String, Int> {
    return buildInitialVisibleThreadCounts(
      knownPaths = knownPaths,
      currentVisibleThreadCounts = mutableState.value.visibleThreadCounts,
    )
  }

  fun updateProject(path: String, transform: (AgentProjectSessions) -> AgentProjectSessions) {
    updateProjects { project ->
      if (project.path == path) transform(project) else project
    }
  }

  fun updateWorktree(projectPath: String, worktreePath: String, transform: (AgentWorktree) -> AgentWorktree) {
    updateProject(projectPath) { project ->
      project.withUpdatedWorktree(worktreePath = worktreePath, transform = transform)
    }
  }

  fun findWorktreeBranch(path: String): String? {
    return mutableState.value.projects
      .asSequence()
      .flatMap { project -> project.worktrees.asSequence() }
      .firstOrNull { worktree -> worktree.path == path }
      ?.branch
  }

  private fun updateProjects(transform: (AgentProjectSessions) -> AgentProjectSessions) {
    update { state ->
      val nextProjects = state.projects.map(transform)
      if (nextProjects == state.projects) state else state.copy(projects = nextProjects, lastUpdatedAt = System.currentTimeMillis())
    }
  }

  private fun buildInitialVisibleThreadCounts(
    knownPaths: List<String>,
    currentVisibleThreadCounts: Map<String, Int>,
  ): Map<String, Int> {
    val normalizedKnownPaths = knownPaths.mapTo(LinkedHashSet()) { normalizeAgentWorkbenchPath(it) }
    val visibleThreadCounts = LinkedHashMap<String, Int>()
    currentVisibleThreadCounts.forEach { (path, count) ->
      val normalized = normalizeAgentWorkbenchPath(path)
      if (normalized in normalizedKnownPaths && count > DEFAULT_VISIBLE_THREAD_COUNT) {
        visibleThreadCounts[normalized] = count
      }
    }
    return visibleThreadCounts
  }

}

private fun findThreadIndex(
  projects: List<AgentProjectSessions>,
  normalizedPath: String,
  provider: AgentSessionProvider,
  threadId: String,
): Int? {
  val projectThreads = projects.firstOrNull { it.path == normalizedPath }?.threads
  projectThreads?.indexOfMatchingThread(provider = provider, threadId = threadId)?.let { index ->
    return index
  }

  return projects
    .asSequence()
    .mapNotNull { project -> project.worktrees.firstOrNull { worktree -> worktree.path == normalizedPath } }
    .firstNotNullOfOrNull { worktree -> worktree.threads.indexOfMatchingThread(provider = provider, threadId = threadId) }
}

private fun AgentSessionThread.matchesProviderAndThreadOrSubAgent(provider: AgentSessionProvider, threadId: String): Boolean {
  return this.provider == provider && (id == threadId || subAgents.any { subAgent -> subAgent.id == threadId })
}

private fun List<AgentSessionThread>.indexOfMatchingThread(provider: AgentSessionProvider, threadId: String): Int? {
  val index = indexOfFirst { thread -> thread.matchesProviderAndThreadOrSubAgent(provider = provider, threadId = threadId) }
  return index.takeIf { it >= 0 }
}

private fun AgentProjectSessions.withUpdatedWorktree(
  worktreePath: String,
  transform: (AgentWorktree) -> AgentWorktree,
): AgentProjectSessions {
  val nextWorktrees = worktrees.map { worktree ->
    if (worktree.path == worktreePath) transform(worktree) else worktree
  }
  return if (nextWorktrees == worktrees) this else copy(worktrees = nextWorktrees)
}

private fun requiredVisibleClosedProjectCount(projects: List<AgentProjectSessions>, normalizedPath: String): Int? {
  var closedProjectCount = 0
  projects.forEach { project ->
    val isAlwaysVisible = project.isOpen || project.worktrees.any { it.isOpen }
    if (!isAlwaysVisible) {
      closedProjectCount++
    }
    if (normalizeAgentWorkbenchPath(project.path) == normalizedPath) {
      return if (isAlwaysVisible) 0 else closedProjectCount
    }
  }
  return null
}
