// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
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
      shouldLoadOpenPath(
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
      val providerLoadStates = mergeWarmAndRuntimeProviderLoadStates(
        warmProviderLoadStates = warmSnapshot?.providerLoadStates.orEmpty(),
        runtimeProviderLoadStates = existing?.providerLoadStates.orEmpty(),
      )
      val providersWithUnknownThreadCount = mergeWarmAndRuntimeUnknownThreadCountProviders(
        warmProvidersWithUnknownThreadCount = warmSnapshot?.providersWithUnknownThreadCount.orEmpty(),
        runtimeProviderLoadStates = existing?.providerLoadStates.orEmpty(),
        runtimeProvidersWithUnknownThreadCount = existing?.providersWithUnknownThreadCount.orEmpty(),
        mergedProviderLoadStates = providerLoadStates,
      )
      AgentProjectSessions(
        path = normalizedEntryPath,
        name = entry.name,
        branch = entry.branch,
        buildSystemBadge = entry.buildSystemBadge,
        isOpen = entryIsOpen,
        threads = existing?.threads ?: cachedThreads,
        errorMessage = existing?.errorMessage,
        providerWarnings = existing?.providerWarnings ?: emptyList(),
        providerLoadStates = providerLoadStates,
        providersWithUnknownThreadCount = providersWithUnknownThreadCount,
        worktrees = entry.worktreeEntries.map { wt ->
          val normalizedWorktreePath = normalizeAgentWorkbenchPath(wt.path)
          knownPaths.add(normalizedWorktreePath)
          val existingWt = existing?.worktrees?.firstOrNull { normalizeAgentWorkbenchPath(it.path) == normalizedWorktreePath }
          val worktreeIsOpen = wt.project != null
          shouldLoadOpenPath(
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
          val worktreeProviderLoadStates = mergeWarmAndRuntimeProviderLoadStates(
            warmProviderLoadStates = warmWorktreeSnapshot?.providerLoadStates.orEmpty(),
            runtimeProviderLoadStates = existingWt?.providerLoadStates.orEmpty(),
          )
          val worktreeProvidersWithUnknownThreadCount = mergeWarmAndRuntimeUnknownThreadCountProviders(
            warmProvidersWithUnknownThreadCount = warmWorktreeSnapshot?.providersWithUnknownThreadCount.orEmpty(),
            runtimeProviderLoadStates = existingWt?.providerLoadStates.orEmpty(),
            runtimeProvidersWithUnknownThreadCount = existingWt?.providersWithUnknownThreadCount.orEmpty(),
            mergedProviderLoadStates = worktreeProviderLoadStates,
          )
          AgentWorktree(
            path = normalizedWorktreePath,
            name = wt.name,
            branch = wt.branch,
            isOpen = worktreeIsOpen,
            threads = existingWt?.threads ?: cachedWorktreeThreads,
            errorMessage = existingWt?.errorMessage,
            providerWarnings = existingWt?.providerWarnings ?: emptyList(),
            providerLoadStates = worktreeProviderLoadStates,
            providersWithUnknownThreadCount = worktreeProvidersWithUnknownThreadCount,
          )
        },
      )
    }
    return RefreshBootstrap(
      entries = entries,
      knownPaths = knownPaths.toSet(),
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

private fun mergeWarmAndRuntimeProviderLoadStates(
  warmProviderLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState>,
  runtimeProviderLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState>,
): Map<AgentSessionProvider, AgentSessionProviderLoadState> {
  if (warmProviderLoadStates.isEmpty()) {
    return runtimeProviderLoadStates
  }
  if (runtimeProviderLoadStates.isEmpty()) {
    return warmProviderLoadStates
  }
  return warmProviderLoadStates + runtimeProviderLoadStates
}

private fun mergeWarmAndRuntimeUnknownThreadCountProviders(
  warmProvidersWithUnknownThreadCount: Set<AgentSessionProvider>,
  runtimeProviderLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState>,
  runtimeProvidersWithUnknownThreadCount: Set<AgentSessionProvider>,
  mergedProviderLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState>,
): Set<AgentSessionProvider> {
  return buildSet {
    warmProvidersWithUnknownThreadCount.filterTo(this) { provider -> provider !in runtimeProviderLoadStates }
    addAll(runtimeProvidersWithUnknownThreadCount)
  }.filterTo(LinkedHashSet()) { provider -> mergedProviderLoadStates[provider] == AgentSessionProviderLoadState.LOADED }
}

internal enum class RefreshLoadScope {
  NEWLY_OPENED_ONLY,
  ALL_OPEN_PROJECTS,
}

internal data class RefreshBootstrap(
  val entries: List<ProjectEntry>,
  val knownPaths: Set<String>,
  val openPaths: Set<String>,
  val loadPaths: Set<String>,
  val initialProjects: List<AgentProjectSessions>,
  val initialVisibleThreadCounts: Map<String, Int>,
)
