// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.state

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
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
          project.copy(
            isLoading = false,
            hasLoaded = true,
            hasUnknownThreadCount = false,
            errorMessage = errorMessage,
            providerWarnings = emptyList(),
            providerLoadStates = project.providerLoadStates.failLoadingProviders(),
            worktrees = project.worktrees.map { wt ->
              wt.copy(
                isLoading = false,
                hasUnknownThreadCount = false,
                providerWarnings = emptyList(),
                providerLoadStates = wt.providerLoadStates.failLoadingProviders(),
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
    update { state ->
      var changed = false
      val next = state.projects.map { project ->
        if (project.path != path) {
          project
        }
        else {
          val updatedProject = transform(project)
          if (updatedProject == project) {
            project
          }
          else {
            changed = true
            updatedProject
          }
        }
      }
      if (changed) state.copy(projects = next, lastUpdatedAt = System.currentTimeMillis()) else state
    }
  }

  fun updateWorktree(projectPath: String, worktreePath: String, transform: (AgentWorktree) -> AgentWorktree) {
    update { state ->
      var changed = false
      val next = state.projects.map { project ->
        if (project.path == projectPath) {
          val nextWorktrees = project.worktrees.map { wt ->
            if (wt.path != worktreePath) {
              wt
            }
            else {
              val updatedWorktree = transform(wt)
              if (updatedWorktree == wt) {
                wt
              }
              else {
                changed = true
                updatedWorktree
              }
            }
          }
          if (changed) project.copy(worktrees = nextWorktrees) else project
        }
        else {
          project
        }
      }
      if (changed) state.copy(projects = next, lastUpdatedAt = System.currentTimeMillis()) else state
    }
  }

  fun findWorktreeBranch(path: String): String? {
    for (project in mutableState.value.projects) {
      for (worktree in project.worktrees) {
        if (worktree.path == path) return worktree.branch
      }
    }
    return null
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

private fun Map<AgentSessionProvider, AgentSessionProviderLoadState>.failLoadingProviders() =
  if (isEmpty() || values.none { it == AgentSessionProviderLoadState.LOADING }) {
    this
  }
  else {
    mapValues { (_, state) ->
      if (state == AgentSessionProviderLoadState.LOADING) AgentSessionProviderLoadState.FAILED else state
    }
  }

private fun findThreadIndex(
  projects: List<AgentProjectSessions>,
  normalizedPath: String,
  provider: AgentSessionProvider,
  threadId: String,
): Int? {
  val projectThreads = projects.firstOrNull { it.path == normalizedPath }?.threads
  if (projectThreads != null) {
    val index = projectThreads.indexOfFirst { thread ->
      thread.matchesProviderAndThreadOrSubAgent(provider = provider, threadId = threadId)
    }
    if (index >= 0) return index
  }

  projects.forEach { project ->
    val worktreeThreads = project.worktrees.firstOrNull { it.path == normalizedPath }?.threads ?: return@forEach
    val index = worktreeThreads.indexOfFirst { thread ->
      thread.matchesProviderAndThreadOrSubAgent(provider = provider, threadId = threadId)
    }
    if (index >= 0) return index
  }

  return null
}

private fun AgentSessionThread.matchesProviderAndThreadOrSubAgent(provider: AgentSessionProvider, threadId: String): Boolean {
  return this.provider == provider && (id == threadId || subAgents.any { subAgent -> subAgent.id == threadId })
}

private fun requiredVisibleClosedProjectCount(projects: List<AgentProjectSessions>, normalizedPath: String): Int? {
  var closedProjectCount = 0
  for (project in projects) {
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
