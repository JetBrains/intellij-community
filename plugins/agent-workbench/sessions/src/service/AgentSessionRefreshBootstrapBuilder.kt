// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.model.ProjectEntry
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore

internal class AgentSessionRefreshBootstrapBuilder(
  private val projectEntriesProvider: suspend () -> List<ProjectEntry>,
  private val stateStore: AgentSessionsStateStore,
  private val contentRepository: AgentSessionContentRepository,
) {
  suspend fun build(
    currentState: AgentSessionsState,
    loadScope: RefreshLoadScope,
  ): RefreshBootstrap {
    val entries = projectEntriesProvider()
    val currentProjectsByPath = currentState.projects.associateBy { normalizeAgentWorkbenchPath(it.path) }
    val openPaths = LinkedHashSet<String>()
    val loadPaths = LinkedHashSet<String>()
    val knownPaths = ArrayList<String>()
    val initialProjects = entries.map { entry ->
      val normalizedEntryPath = normalizeAgentWorkbenchPath(entry.path)
      knownPaths.add(normalizedEntryPath)
      val existing = currentProjectsByPath[normalizedEntryPath]
      val entryIsOpen = entry.project != null
      val shouldLoadProject = shouldLoadOpenPath(
        isOpen = entryIsOpen,
        wasOpen = existing?.isOpen == true,
        normalizedPath = normalizedEntryPath,
        loadScope = loadScope,
        openPaths = openPaths,
        loadPaths = loadPaths,
      )
      val warmSnapshot = if (entryIsOpen) {
        contentRepository.getWarmSnapshot(normalizedEntryPath)
      }
      else {
        null
      }
      val cachedThreads = warmSnapshot?.threads.orEmpty()
      AgentProjectSessions(
        path = normalizedEntryPath,
        name = entry.name,
        branch = entry.branch,
        buildSystemBadge = entry.buildSystemBadge,
        isOpen = entryIsOpen,
        isLoading = shouldLoadProject,
        hasLoaded = existing?.hasLoaded ?: (warmSnapshot != null),
        hasUnknownThreadCount = existing?.hasUnknownThreadCount ?: (warmSnapshot?.hasUnknownThreadCount ?: false),
        threads = existing?.threads ?: cachedThreads,
        errorMessage = existing?.errorMessage,
        providerWarnings = existing?.providerWarnings ?: emptyList(),
        worktrees = entry.worktreeEntries.map { wt ->
          val normalizedWorktreePath = normalizeAgentWorkbenchPath(wt.path)
          knownPaths.add(normalizedWorktreePath)
          val existingWt = existing?.worktrees?.firstOrNull { normalizeAgentWorkbenchPath(it.path) == normalizedWorktreePath }
          val worktreeIsOpen = wt.project != null
          val shouldLoadWorktree = shouldLoadOpenPath(
            isOpen = worktreeIsOpen,
            wasOpen = existingWt?.isOpen == true,
            normalizedPath = normalizedWorktreePath,
            loadScope = loadScope,
            openPaths = openPaths,
            loadPaths = loadPaths,
          )
          val warmWorktreeSnapshot = if (worktreeIsOpen) {
            contentRepository.getWarmSnapshot(normalizedWorktreePath)
          }
          else {
            null
          }
          val cachedWorktreeThreads = warmWorktreeSnapshot?.threads.orEmpty()
          AgentWorktree(
            path = normalizedWorktreePath,
            name = wt.name,
            branch = wt.branch,
            isOpen = worktreeIsOpen,
            isLoading = shouldLoadWorktree,
            hasLoaded = existingWt?.hasLoaded ?: (warmWorktreeSnapshot != null),
            hasUnknownThreadCount = existingWt?.hasUnknownThreadCount ?: (warmWorktreeSnapshot?.hasUnknownThreadCount ?: false),
            threads = existingWt?.threads ?: cachedWorktreeThreads,
            errorMessage = existingWt?.errorMessage,
            providerWarnings = existingWt?.providerWarnings ?: emptyList(),
          )
        },
      )
    }
    return RefreshBootstrap(
      entries = entries,
      openPaths = openPaths,
      loadPaths = loadPaths,
      initialProjects = initialProjects,
      initialVisibleThreadCounts = stateStore.buildInitialVisibleThreadCounts(knownPaths),
    )
  }

  private fun shouldLoadOpenPath(
    isOpen: Boolean,
    wasOpen: Boolean,
    normalizedPath: String,
    loadScope: RefreshLoadScope,
    openPaths: MutableSet<String>,
    loadPaths: MutableSet<String>,
  ): Boolean {
    if (!isOpen) {
      return false
    }
    openPaths.add(normalizedPath)
    val shouldLoad = when (loadScope) {
      RefreshLoadScope.ALL_OPEN_PROJECTS -> true
      RefreshLoadScope.NEWLY_OPENED_ONLY -> !wasOpen
    }
    if (shouldLoad) {
      loadPaths.add(normalizedPath)
    }
    return shouldLoad
  }
}

internal enum class RefreshLoadScope {
  NEWLY_OPENED_ONLY,
  ALL_OPEN_PROJECTS,
}

internal data class RefreshBootstrap(
  val entries: List<ProjectEntry>,
  val openPaths: Set<String>,
  val loadPaths: Set<String>,
  val initialProjects: List<AgentProjectSessions>,
  val initialVisibleThreadCounts: Map<String, Int>,
)
