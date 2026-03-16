// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

internal class AgentSessionsStateStore(
  private val treeUiState: SessionsTreeUiState,
) {
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
    mutableState.update { it.copy(visibleProjectCount = it.visibleProjectCount + DEFAULT_VISIBLE_PROJECT_COUNT) }
  }

  fun showMoreThreads(path: String) {
    val normalizedPath = normalizePath(path)
    var deltaToPersist = 0
    mutableState.update { state ->
      val current = state.visibleThreadCounts[normalizedPath] ?: treeUiState.getVisibleThreadCount(normalizedPath)
      val nextVisible = current + DEFAULT_VISIBLE_THREAD_COUNT
      deltaToPersist = nextVisible - current
      state.copy(visibleThreadCounts = state.visibleThreadCounts + (normalizedPath to nextVisible))
    }
    if (deltaToPersist > 0) {
      treeUiState.incrementVisibleThreadCount(normalizedPath, deltaToPersist)
    }
  }

  fun ensureThreadVisible(path: String, provider: AgentSessionProvider, threadId: String) {
    val normalizedPath = normalizePath(path)
    var deltaToPersist = 0
    mutableState.update { state ->
      val threadIndex = findThreadIndex(
        projects = state.projects,
        normalizedPath = normalizedPath,
        provider = provider,
        threadId = threadId,
      ) ?: return@update state
      val currentVisible = state.visibleThreadCounts[normalizedPath] ?: treeUiState.getVisibleThreadCount(normalizedPath)
      if (threadIndex < currentVisible) {
        return@update state
      }
      val minVisible = threadIndex + 1
      var nextVisible = currentVisible
      while (nextVisible < minVisible) {
        nextVisible += DEFAULT_VISIBLE_THREAD_COUNT
      }
      deltaToPersist = nextVisible - currentVisible
      state.copy(visibleThreadCounts = state.visibleThreadCounts + (normalizedPath to nextVisible))
    }
    if (deltaToPersist > 0) {
      treeUiState.incrementVisibleThreadCount(normalizedPath, deltaToPersist)
    }
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
    val normalizedKnownPaths = knownPaths.mapTo(LinkedHashSet()) { normalizePath(it) }
    val visibleThreadCounts = LinkedHashMap<String, Int>()
    currentVisibleThreadCounts.forEach { (path, count) ->
      val normalized = normalizePath(path)
      if (normalized in normalizedKnownPaths && count > DEFAULT_VISIBLE_THREAD_COUNT) {
        visibleThreadCounts[normalized] = count
      }
    }
    for (path in normalizedKnownPaths) {
      if (path in visibleThreadCounts) continue
      val persisted = treeUiState.getVisibleThreadCount(path)
      if (persisted > DEFAULT_VISIBLE_THREAD_COUNT) {
        visibleThreadCounts[path] = persisted
      }
    }
    return visibleThreadCounts
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

  private fun normalizePath(path: String): String {
    return try {
      Path.of(path).invariantSeparatorsPathString
    }
    catch (_: InvalidPathException) {
      path
    }
  }
}
