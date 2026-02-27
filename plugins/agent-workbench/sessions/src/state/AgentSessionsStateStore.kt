// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.state

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class AgentSessionsStateStore {
  private val mutableState = MutableStateFlow(AgentSessionsState())
  val state: StateFlow<AgentSessionsState> = mutableState.asStateFlow()

  fun snapshot(): AgentSessionsState = mutableState.value

  fun update(transform: (AgentSessionsState) -> AgentSessionsState) {
    mutableState.update(transform)
  }

  fun replaceProjects(projects: List<AgentProjectSessions>, visibleThreadCounts: Map<String, Int>) {
    mutableState.update {
      it.copy(
        projects = projects,
        visibleThreadCounts = visibleThreadCounts,
        lastUpdatedAt = System.currentTimeMillis(),
      )
    }
  }

  fun markLoadFailure(errorMessage: String) {
    mutableState.update { state ->
      state.copy(
        projects = state.projects.map { project ->
          project.copy(
            isLoading = false,
            hasLoaded = true,
            hasUnknownThreadCount = false,
            errorMessage = errorMessage,
            providerWarnings = emptyList(),
            worktrees = project.worktrees.map { wt ->
              wt.copy(isLoading = false, hasUnknownThreadCount = false, providerWarnings = emptyList())
            },
          )
        },
        lastUpdatedAt = System.currentTimeMillis(),
      )
    }
  }

  fun showMoreProjects() {
    mutableState.update {
      it.copy(
        visibleClosedProjectCount = it.visibleClosedProjectCount + DEFAULT_VISIBLE_CLOSED_PROJECT_COUNT,
      )
    }
  }

  fun showMoreThreads(path: String) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    mutableState.update { state ->
      val current = state.visibleThreadCounts[normalizedPath] ?: DEFAULT_VISIBLE_THREAD_COUNT
      val nextVisible = current + DEFAULT_VISIBLE_THREAD_COUNT
      state.copy(visibleThreadCounts = state.visibleThreadCounts + (normalizedPath to nextVisible))
    }
  }

  fun ensureThreadVisible(path: String, provider: AgentSessionProvider, threadId: String) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    mutableState.update { state ->
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

  fun removeThread(path: String, provider: AgentSessionProvider, threadId: String): Boolean {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    var removed = false
    mutableState.update { state ->
      val nextProjects = state.projects.map { project ->
        if (project.path == normalizedPath) {
          val nextThreads = project.threads.filterNot { it.provider == provider && it.id == threadId }
          if (nextThreads.size != project.threads.size) {
            removed = true
            project.copy(threads = nextThreads)
          }
          else {
            project
          }
        }
        else {
          val nextWorktrees = project.worktrees.map { worktree ->
            if (worktree.path == normalizedPath) {
              val nextThreads = worktree.threads.filterNot { it.provider == provider && it.id == threadId }
              if (nextThreads.size != worktree.threads.size) {
                removed = true
                worktree.copy(threads = nextThreads)
              }
              else {
                worktree
              }
            }
            else {
              worktree
            }
          }
          if (nextWorktrees == project.worktrees) project else project.copy(worktrees = nextWorktrees)
        }
      }
      if (!removed) state else state.copy(projects = nextProjects, lastUpdatedAt = System.currentTimeMillis())
    }
    return removed
  }

  fun buildInitialVisibleThreadCounts(knownPaths: List<String>): Map<String, Int> {
    return buildInitialVisibleThreadCounts(
      knownPaths = knownPaths,
      currentVisibleThreadCounts = mutableState.value.visibleThreadCounts,
    )
  }

  fun updateProject(path: String, update: (AgentProjectSessions) -> AgentProjectSessions) {
    mutableState.update { state ->
      val next = state.projects.map { project ->
        if (project.path == path) update(project) else project
      }
      state.copy(projects = next, lastUpdatedAt = System.currentTimeMillis())
    }
  }

  fun updateWorktree(projectPath: String, worktreePath: String, update: (AgentWorktree) -> AgentWorktree) {
    mutableState.update { state ->
      val next = state.projects.map { project ->
        if (project.path == projectPath) {
          project.copy(worktrees = project.worktrees.map { wt ->
            if (wt.path == worktreePath) update(wt) else wt
          })
        }
        else {
          project
        }
      }
      state.copy(projects = next, lastUpdatedAt = System.currentTimeMillis())
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

private fun findThreadIndex(
  projects: List<AgentProjectSessions>,
  normalizedPath: String,
  provider: AgentSessionProvider,
  threadId: String,
): Int? {
  val projectThreads = projects.firstOrNull { it.path == normalizedPath }?.threads
  if (projectThreads != null) {
    val index = projectThreads.indexOfFirst { it.provider == provider && it.id == threadId }
    if (index >= 0) return index
  }

  projects.forEach { project ->
    val worktreeThreads = project.worktrees.firstOrNull { it.path == normalizedPath }?.threads ?: return@forEach
    val index = worktreeThreads.indexOfFirst { it.provider == provider && it.id == threadId }
    if (index >= 0) return index
  }

  return null
}
