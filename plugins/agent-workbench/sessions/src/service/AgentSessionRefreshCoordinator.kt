// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatConcreteCodexTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatConcreteCodexTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatConcreteCodexTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatOpenTabsRefreshSnapshot
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindRequest
import com.intellij.agent.workbench.chat.agentChatScopedRefreshSignals
import com.intellij.agent.workbench.chat.clearOpenConcreteAgentChatNewThreadRebindAnchors
import com.intellij.agent.workbench.chat.collectOpenAgentChatRefreshSnapshot
import com.intellij.agent.workbench.chat.rebindOpenConcreteAgentChatTabs
import com.intellij.agent.workbench.chat.rebindOpenPendingAgentChatTabs
import com.intellij.agent.workbench.chat.updateOpenAgentChatTabPresentation
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderWarning
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.model.ProjectEntry
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.util.agentSessionCliMissingMessageKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AgentSessionRefreshCoordinator(
    private val serviceScope: CoroutineScope,
    private val sessionSourcesProvider: () -> List<AgentSessionSource>,
    private val projectEntriesProvider: suspend () -> List<ProjectEntry>,
    private val stateStore: AgentSessionsStateStore,
    private val contentRepository: AgentSessionContentRepository,
    private val isRefreshGateActive: suspend () -> Boolean,
    private val openAgentChatSnapshotProvider: suspend () -> AgentChatOpenTabsRefreshSnapshot = ::collectOpenAgentChatRefreshSnapshot,
    codexScopedRefreshSignalsProvider: (AgentSessionProvider) -> Flow<Set<String>> = { provider ->
      agentChatScopedRefreshSignals(provider)
    },
    private val openAgentChatTabPresentationUpdater: suspend (
    Map<Pair<String, String>, String>,
    Map<Pair<String, String>, AgentThreadActivity>,
  ) -> Int = ::updateOpenAgentChatTabPresentation,
  private val openAgentChatPendingTabsBinder: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingCodexTabRebindRequest>>,
  ) -> AgentChatPendingCodexTabRebindReport = ::rebindOpenPendingAgentChatTabs,
  private val openAgentChatConcreteTabsBinder: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatConcreteCodexTabRebindRequest>>,
  ) -> AgentChatConcreteCodexTabRebindReport = ::rebindOpenConcreteAgentChatTabs,
  private val clearOpenConcreteCodexTabAnchors: (
    AgentSessionProvider,
    Map<String, List<AgentChatConcreteCodexTabSnapshot>>,
  ) -> Int = ::clearOpenConcreteAgentChatNewThreadRebindAnchors,
) {
  private val refreshMutex = Mutex()
  private val archiveSuppressionSupport = AgentSessionArchiveSuppressionSupport()
  private val providerRefreshSupportByProvider = LinkedHashMap<AgentSessionProvider, AgentSessionCodexRefreshSupport>()
  private val providerRefreshSupportLock = Any()
  private val threadLoadSupport = AgentSessionThreadLoadSupport(
    sessionSourcesProvider = sessionSourcesProvider,
    applyArchiveSuppressions = archiveSuppressionSupport::apply,
    resolveErrorMessage = ::resolveErrorMessage,
    resolveProviderWarningMessage = ::resolveProviderWarningMessage,
  )
  private val onDemandLoadSupport = AgentSessionOnDemandLoadSupport(
    serviceScope = serviceScope,
    stateStore = stateStore,
    threadLoadSupport = threadLoadSupport,
  )
  private val refreshBootstrapBuilder = AgentSessionRefreshBootstrapBuilder(
    projectEntriesProvider = projectEntriesProvider,
    stateStore = stateStore,
    contentRepository = contentRepository,
  )
  private val providerRefreshRunner = AgentSessionProviderRefreshRunner(
    refreshMutex = refreshMutex,
    sessionSourcesProvider = sessionSourcesProvider,
    stateStore = stateStore,
    contentRepository = contentRepository,
    archiveSuppressionSupport = archiveSuppressionSupport,
    refreshSupportProvider = ::refreshSupportFor,
    resolveProviderWarningMessage = ::resolveProviderWarningMessage,
    openAgentChatSnapshotProvider = openAgentChatSnapshotProvider,
    openAgentChatTabPresentationUpdater = openAgentChatTabPresentationUpdater,
  )
  private val refreshScheduler = AgentSessionRefreshScheduler(
    serviceScope = serviceScope,
    sessionSourcesProvider = sessionSourcesProvider,
    scopedRefreshProvidersProvider = {
      AgentSessionProviders.allProvidersById()
        .asSequence()
        .filter { provider -> provider.emitsScopedRefreshSignals }
        .map { provider -> provider.provider }
        .toList()
    },
    scopedRefreshSignalsProvider = codexScopedRefreshSignalsProvider,
    isRefreshGateActive = isRefreshGateActive,
    executeFullRefresh = ::refreshNow,
    executeProviderRefresh = providerRefreshRunner::refreshLoadedProviderThreads,
    onFullRefreshFailure = {
      stateStore.markLoadFailure(AgentSessionsBundle.message("toolwindow.error"))
    },
  )

  fun observeSessionSourceUpdates() {
    refreshScheduler.observeSessionSourceUpdates()
  }

  private fun refreshSupportFor(provider: AgentSessionProvider): AgentSessionCodexRefreshSupport? {
    val descriptor = AgentSessionProviders.find(provider) ?: return null
    if (!descriptor.supportsPendingEditorTabRebind && !descriptor.supportsNewThreadRebind) {
      return null
    }
    return synchronized(providerRefreshSupportLock) {
      providerRefreshSupportByProvider.getOrPut(provider) {
        AgentSessionCodexRefreshSupport(
          provider = provider,
          openAgentChatPendingTabsBinder = openAgentChatPendingTabsBinder,
          openAgentChatConcreteTabsBinder = openAgentChatConcreteTabsBinder,
          clearOpenConcreteCodexTabAnchors = clearOpenConcreteCodexTabAnchors,
        )
      }
    }
  }

  fun refresh() {
    refreshScheduler.refresh()
  }

  fun refreshCatalogAndLoadNewlyOpened() {
    refreshScheduler.refreshCatalogAndLoadNewlyOpened()
  }

  internal fun refreshProviderScope(provider: AgentSessionProvider, scopedPaths: Set<String>) {
    refreshScheduler.refreshProviderScope(provider = provider, scopedPaths = scopedPaths)
  }

  private suspend fun refreshNow(loadScope: RefreshLoadScope) {
    refreshMutex.withLock {
      val currentState = stateStore.snapshot()
      val bootstrap = refreshBootstrapBuilder.build(currentState = currentState, loadScope = loadScope)
      contentRepository.retainWarmSnapshots(bootstrap.openPaths)
      stateStore.replaceProjects(
        projects = bootstrap.initialProjects,
        visibleThreadCounts = bootstrap.initialVisibleThreadCounts,
      )

      if (bootstrap.loadPaths.isEmpty()) {
        stateStore.update { it.copy(lastUpdatedAt = System.currentTimeMillis()) }
        return
      }

      val sessionSources = sessionSourcesProvider()

      val prefetchedByProvider = coroutineScope {
        sessionSources.map { source ->
          async {
            source.provider to try {
              source.prefetchThreads(bootstrap.loadPaths.toList())
            }
            catch (_: Throwable) {
              emptyMap()
            }
          }
        }.awaitAll().toMap()
      }

      coroutineScope {
        for (entry in bootstrap.entries) {
          launch {
            val normalizedEntryPath = normalizeAgentWorkbenchPath(entry.path)
            val entryProject = entry.project
            val shouldLoadProject = entryProject != null && normalizedEntryPath in bootstrap.loadPaths
            if (!shouldLoadProject) {
              stateStore.updateProject(normalizedEntryPath) { project ->
                if (project.isLoading) project.copy(isLoading = false) else project
              }
              return@launch
            }
            val finalResult = threadLoadSupport.loadSourcesIncrementally(
              sessionSources = sessionSources,
              normalizedPath = normalizedEntryPath,
              project = entryProject,
              prefetchedByProvider = prefetchedByProvider,
              originalPath = entry.path,
            ) { partial, isComplete ->
              stateStore.updateProject(normalizedEntryPath) { project ->
                project.copy(
                  threads = partial.threads,
                  providerWarnings = partial.providerWarnings,
                  isLoading = !isComplete,
                )
              }
            }
            stateStore.updateProject(normalizedEntryPath) { project ->
              project.copy(
                isLoading = false,
                hasLoaded = true,
                hasUnknownThreadCount = finalResult.hasUnknownThreadCount,
                threads = finalResult.threads,
                errorMessage = finalResult.errorMessage,
                providerWarnings = finalResult.providerWarnings,
              )
            }
            contentRepository.syncWarmSnapshotFromRuntime(normalizedEntryPath)
          }
          for (wt in entry.worktreeEntries) {
            launch {
              val normalizedEntryPath = normalizeAgentWorkbenchPath(entry.path)
              val normalizedWorktreePath = normalizeAgentWorkbenchPath(wt.path)
              val worktreeProject = wt.project
              val shouldLoadWorktree = worktreeProject != null && normalizedWorktreePath in bootstrap.loadPaths
              if (!shouldLoadWorktree) {
                stateStore.updateWorktree(normalizedEntryPath, normalizedWorktreePath) { worktree ->
                  if (worktree.isLoading) worktree.copy(isLoading = false) else worktree
                }
                return@launch
              }
              val finalResult = threadLoadSupport.loadSourcesIncrementally(
                sessionSources = sessionSources,
                normalizedPath = normalizedWorktreePath,
                project = worktreeProject,
                prefetchedByProvider = prefetchedByProvider,
                originalPath = wt.path,
              ) { partial, isComplete ->
                stateStore.updateWorktree(normalizedEntryPath, normalizedWorktreePath) { worktree ->
                  worktree.copy(
                    threads = partial.threads,
                    providerWarnings = partial.providerWarnings,
                    isLoading = !isComplete,
                  )
                }
              }
              stateStore.updateWorktree(normalizedEntryPath, normalizedWorktreePath) { worktree ->
                worktree.copy(
                  isLoading = false,
                  hasLoaded = true,
                  hasUnknownThreadCount = finalResult.hasUnknownThreadCount,
                  threads = finalResult.threads,
                  errorMessage = finalResult.errorMessage,
                  providerWarnings = finalResult.providerWarnings,
                )
              }
              contentRepository.syncWarmSnapshotFromRuntime(normalizedWorktreePath)
            }
          }
        }
      }
      stateStore.update { it.copy(lastUpdatedAt = System.currentTimeMillis()) }
    }
  }

  fun suppressArchivedTarget(target: ArchiveThreadTarget) {
    archiveSuppressionSupport.suppress(target)
  }

  fun unsuppressArchivedTarget(target: ArchiveThreadTarget) {
    archiveSuppressionSupport.unsuppress(target)
  }

  fun loadProjectThreadsOnDemand(path: String) {
    onDemandLoadSupport.loadProjectThreadsOnDemand(path)
  }

  fun loadWorktreeThreadsOnDemand(projectPath: String, worktreePath: String) {
    onDemandLoadSupport.loadWorktreeThreadsOnDemand(projectPath = projectPath, worktreePath = worktreePath)
  }

  fun appendProviderUnavailableWarning(path: String, provider: AgentSessionProvider) {
    val warning = AgentSessionProviderWarning(provider = provider, message = providerUnavailableMessage(provider))
    stateStore.update { state ->
      var updated = false
      val nextProjects = state.projects.map { project ->
        if (project.path == path) {
          updated = true
          project.copy(providerWarnings = mergeProviderWarning(project.providerWarnings, warning))
        }
        else {
          val nextWorktrees = project.worktrees.map { worktree ->
            if (worktree.path == path) {
              updated = true
              worktree.copy(providerWarnings = mergeProviderWarning(worktree.providerWarnings, warning))
            }
            else {
              worktree
            }
          }
          if (nextWorktrees == project.worktrees) project else project.copy(worktrees = nextWorktrees)
        }
      }
      if (!updated) state else state.copy(projects = nextProjects, lastUpdatedAt = System.currentTimeMillis())
    }
  }

}

private fun resolveErrorMessage(provider: AgentSessionProvider, t: Throwable): String {
  return if (isCliMissingError(provider, t)) resolveCliMissingMessage(provider)
  else AgentSessionsBundle.message("toolwindow.error")
}

private fun resolveCliMissingMessage(provider: AgentSessionProvider): String {
  return if (AgentSessionProviders.find(provider) != null) {
    AgentSessionsBundle.message(agentSessionCliMissingMessageKey(provider))
  }
  else {
    providerUnavailableMessage(provider)
  }
}

private fun resolveProviderWarningMessage(provider: AgentSessionProvider, t: Throwable): String {
  return if (isCliMissingError(provider, t)) resolveCliMissingMessage(provider)
  else AgentSessionsBundle.message("toolwindow.warning.provider.unavailable", resolveProviderLabel(provider))
}

private fun isCliMissingError(provider: AgentSessionProvider, t: Throwable): Boolean {
  return AgentSessionProviders.find(provider)?.isCliMissingError(t) == true
}

private fun resolveProviderLabel(provider: AgentSessionProvider): String {
  val bridge = AgentSessionProviders.find(provider)
  return if (bridge != null) AgentSessionsBundle.message(bridge.displayNameKey) else provider.value
}

private fun providerUnavailableMessage(provider: AgentSessionProvider): String {
  return AgentSessionsBundle.message("toolwindow.warning.provider.unavailable", resolveProviderLabel(provider))
}

private fun mergeProviderWarning(
  warnings: List<AgentSessionProviderWarning>,
  warning: AgentSessionProviderWarning,
): List<AgentSessionProviderWarning> {
  if (warnings.any { it.provider == warning.provider && it.message == warning.message }) {
    return warnings
  }
  return warnings + warning
}
