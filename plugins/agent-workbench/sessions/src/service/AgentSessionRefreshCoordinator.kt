// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatConcreteTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatOpenTabsRefreshSnapshot
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindRequest
import com.intellij.agent.workbench.chat.agentChatScopedRefreshSignals
import com.intellij.agent.workbench.chat.clearOpenConcreteAgentChatNewThreadRebindAnchors
import com.intellij.agent.workbench.chat.collectOpenAgentChatRefreshSnapshot
import com.intellij.agent.workbench.chat.rebindOpenPendingAgentChatTabs
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.parseAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationPatchUpdate
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationModel
import com.intellij.agent.workbench.sessions.core.config.AgentWorkbenchProjectRuntimeConfigs
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadPresentationUpdate
import com.intellij.agent.workbench.sessions.core.providers.isUnscoped
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderWarning
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.model.ProjectEntry
import com.intellij.agent.workbench.sessions.settings.AgentSessionProviderSettingsService
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.util.agentSessionCliMissingMessageKey
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<AgentSessionRefreshCoordinator>()

internal class AgentSessionRefreshCoordinator(
  private val serviceScope: CoroutineScope,
  private val sessionSourcesProvider: () -> List<AgentSessionSource>,
  private val projectEntriesProvider: suspend () -> List<ProjectEntry>,
  private val stateStore: AgentSessionsStateStore,
  private val contentRepository: AgentSessionContentRepository,
  private val isRefreshGateActive: suspend () -> Boolean,
  private val scheduleVfsRefresh: (Set<String>) -> Unit = ::scheduleAgentWorkbenchVfsRefresh,
  private val isVfsRefreshOnStatusUpdatesEnabled: (String) -> Boolean =
    AgentWorkbenchProjectRuntimeConfigs::isRefreshVfsOnStatusUpdatesEnabled,
  private val openAgentChatSnapshotProvider: suspend () -> AgentChatOpenTabsRefreshSnapshot = ::collectOpenAgentChatRefreshSnapshot,
  private val providerDescriptorsByIdProvider: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProvidersById,
  private val providerDescriptorProvider: (AgentSessionProvider) -> AgentSessionProviderDescriptor? = AgentSessionProviders::find,
  scopedRefreshSignalsProvider: (AgentSessionProvider) -> Flow<AgentSessionSourceUpdateEvent> = { provider ->
    agentChatScopedRefreshSignals(provider)
  },
  private val presentationModel: AgentSessionThreadPresentationModel = service<AgentSessionThreadPresentationModel>(),
  private val openAgentChatPendingTabsBinder: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = ::rebindOpenPendingAgentChatTabs,
  private val clearOpenConcreteNewThreadRebindAnchors: (
    AgentSessionProvider,
    Map<String, List<AgentChatConcreteTabSnapshot>>,
  ) -> Int = ::clearOpenConcreteAgentChatNewThreadRebindAnchors,
) {
  private val refreshMutex = Mutex()
  private val archiveSuppressionSupport = AgentSessionArchiveSuppressionSupport()
  private val providerRefreshSupportByProvider = LinkedHashMap<AgentSessionProvider, AgentSessionThreadRebindSupport>()
  private val providerRefreshSupportLock = Any()
  private val threadLoadSupport = AgentSessionThreadLoadSupport(
    sessionSourcesProvider = sessionSourcesProvider,
    applyArchiveSuppressions = archiveSuppressionSupport::apply,
    resolveErrorMessage = ::resolveErrorMessage,
    resolveProviderWarningMessage = ::resolveProviderWarningMessage,
    providerDescriptorProvider = providerDescriptorProvider,
  )
  private val onDemandLoadSupport = AgentSessionOnDemandLoadSupport(
    serviceScope = serviceScope,
    stateStore = stateStore,
    threadLoadSupport = threadLoadSupport,
    sessionSourcesProvider = sessionSourcesProvider,
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
    presentationModel = presentationModel,
  )
  private val refreshScheduler = AgentSessionRefreshScheduler(
    serviceScope = serviceScope,
    sessionSourcesProvider = sessionSourcesProvider,
    scopedRefreshProvidersProvider = {
      service<AgentSessionProviderSettingsService>().enabledProviders(providerDescriptorsByIdProvider())
        .asSequence()
        .filter { provider ->
          provider.emitsScopedRefreshSignals || provider.supportsPendingEditorTabRebind || provider.supportsNewThreadRebind
        }
        .map { provider -> provider.provider }
        .toList()
    },
    scopedRefreshSignalsProvider = scopedRefreshSignalsProvider,
    isRefreshGateActive = isRefreshGateActive,
    executeFullRefresh = ::refreshNow,
    executeProviderRefresh = providerRefreshRunner::refreshLoadedProviderThreads,
    executeProviderHintRefresh = providerRefreshRunner::refreshLoadedProviderHints,
    applySourceUpdatePresentationHints = ::applySourceUpdatePresentationHints,
    scheduleVfsRefreshForSourceUpdate = ::scheduleVfsRefreshForSourceUpdate,
    onFullRefreshFailure = {
      stateStore.markLoadFailure(AgentSessionsBundle.message("toolwindow.error"))
    },
  )

  fun observeSessionSourceUpdates() {
    refreshScheduler.observeSessionSourceUpdates()
  }

  private fun refreshSupportFor(provider: AgentSessionProvider): AgentSessionThreadRebindSupport? {
    val descriptor = providerDescriptorProvider(provider) ?: return null
    if (!descriptor.supportsPendingEditorTabRebind && !descriptor.supportsNewThreadRebind) {
      return null
    }
    return synchronized(providerRefreshSupportLock) {
      providerRefreshSupportByProvider.getOrPut(provider) {
        AgentSessionThreadRebindSupport(
          provider = provider,
          canBindPendingOpenChatTabs = descriptor.supportsPendingEditorTabRebind,
          canRebindConcreteNewThreads = descriptor.supportsNewThreadRebind,
          openAgentChatPendingTabsBinder = openAgentChatPendingTabsBinder,
          clearOpenConcreteNewThreadRebindAnchors = clearOpenConcreteNewThreadRebindAnchors,
        )
      }
    }
  }

  private fun applySourceUpdatePresentationHints(provider: AgentSessionProvider, updateEvent: AgentSessionSourceUpdateEvent) {
    val presentationUpdatesByThreadId = updateEvent.presentationUpdatesByThreadId
    if (presentationUpdatesByThreadId.isEmpty()) {
      return
    }

    val normalizedScopedPaths = updateEvent.scopedPaths
      ?.asSequence()
      ?.map(::normalizeAgentWorkbenchPath)
      ?.filter(String::isNotBlank)
      ?.toCollection(LinkedHashSet())

    var presentationUpdates: List<AgentSessionThreadPresentationPatchUpdate> = emptyList()
    stateStore.update { state ->
      val update = applyThreadPresentationHints(
        state = state,
        provider = provider,
        normalizedScopedPaths = normalizedScopedPaths,
        presentationUpdatesByThreadId = presentationUpdatesByThreadId,
      )
      presentationUpdates = update.presentationUpdates
      update.state
    }

    LOG.debug {
      "Applied ${provider.value} source presentation hints " +
      "scopedPaths=${normalizedScopedPaths.debugSizeText()} " +
      "presentationUpdates=${presentationUpdatesByThreadId.size} " +
      "updates=${presentationUpdates.size}"
    }

    if (presentationUpdates.isEmpty()) {
      return
    }
    serviceScope.launch {
      presentationModel.updatePresentationHints(provider = provider, updates = presentationUpdates)
    }
  }

  private fun scheduleVfsRefreshForSourceUpdate(provider: AgentSessionProvider, updateEvent: AgentSessionSourceUpdateEvent) {
    if (!updateEvent.mayHaveChangedProjectFiles) {
      LOG.debug {
        "Skipped VFS refresh for ${provider.value} source update: no project file change evidence"
      }
      return
    }
    val stateSnapshot = stateStore.snapshot()
    val candidatePaths = collectVfsRefreshCandidatePaths(
      state = stateSnapshot,
      provider = provider,
      updateEvent = updateEvent,
    )
    if (candidatePaths.isEmpty()) {
      LOG.debug {
        "Skipped VFS refresh for ${provider.value} source update: no resolved project paths"
      }
      return
    }
    updateEvent.changedProjectFilePaths?.let { changedProjectFilePaths ->
      val exactRefreshPaths = collectExactVfsRefreshPaths(
        ownerRootPaths = candidatePaths,
        changedProjectFilePaths = changedProjectFilePaths,
        isOwnerRootRefreshEnabled = isVfsRefreshOnStatusUpdatesEnabled,
      )
      if (exactRefreshPaths.paths.isEmpty()) {
        LOG.debug {
          "Skipped exact VFS refresh for ${provider.value} source update: " +
          "changedProjectFiles=${changedProjectFilePaths.size}, skippedOutsideRoot=${exactRefreshPaths.skippedOutsideRoot}, " +
          "disabledRoots=${exactRefreshPaths.disabledRoots}"
        }
        return
      }
      LOG.debug {
        "Scheduling exact VFS refresh for ${provider.value} source update: " +
        "changedProjectFiles=${changedProjectFilePaths.size}, scheduledPaths=${exactRefreshPaths.paths.size}, " +
        "skippedOutsideRoot=${exactRefreshPaths.skippedOutsideRoot}, disabledRoots=${exactRefreshPaths.disabledRoots}"
      }
      scheduleVfsRefresh(exactRefreshPaths.paths)
      return
    }
    val enabledCandidatePaths = candidatePaths.filter(isVfsRefreshOnStatusUpdatesEnabled).toSet()
    if (enabledCandidatePaths.isEmpty()) {
      LOG.debug {
        "Skipped VFS refresh for ${provider.value} source update: disabled by project config paths=${candidatePaths.size}"
      }
      return
    }
    scheduleVfsRefresh(enabledCandidatePaths)
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
      val cliAvailabilityByProvider = resolveCliAvailabilityByProvider(sessionSources)
      val availableSessionSources = sessionSources.filter { source -> cliAvailabilityByProvider[source.provider] != false }
      val loadingProviderLoadStates = buildLoadingProviderLoadStates(availableSessionSources.map { source -> source.provider })
      markProviderLoadStatesLoading(bootstrap = bootstrap, providerLoadStates = loadingProviderLoadStates)

      val prefetchedByProvider = coroutineScope {
        availableSessionSources.map { source ->
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
        for ((entryPath, _, entryProject, _, _, worktreeEntries) in bootstrap.entries) {
          launch {
            val normalizedEntryPath = normalizeAgentWorkbenchPath(entryPath)
            val shouldLoadProject = entryProject != null && normalizedEntryPath in bootstrap.loadPaths
            if (!shouldLoadProject) {
              stateStore.updateProject(normalizedEntryPath) { project ->
                project.withLoadingProvidersFailed()
              }
              return@launch
            }
            val finalResult = threadLoadSupport.loadSourcesIncrementally(
              sessionSources = availableSessionSources,
              normalizedPath = normalizedEntryPath,
              project = entryProject,
              prefetchedByProvider = prefetchedByProvider,
              originalPath = entryPath,
              cliAvailabilityByProvider = cliAvailabilityByProvider,
            ) { partial, _ ->
              stateStore.updateProject(normalizedEntryPath) { project ->
                val refreshedThreads = preserveThreadCosts(
                  existingThreads = project.threads,
                  newThreads = archiveSuppressionSupport.apply(normalizedEntryPath, partial.threads),
                )
                val providerLoadMetadata = mergeProviderLoadMetadata(
                  currentProviderLoadStates = project.providerLoadStates,
                  currentProvidersWithUnknownThreadCount = project.providersWithUnknownThreadCount,
                  providerLoadStateUpdates = partial.providerLoadStates,
                  updatedProvidersWithUnknownThreadCount = partial.providersWithUnknownThreadCount,
                )
                project.copy(
                  threads = refreshedThreads,
                  providerWarnings = partial.providerWarnings,
                  providerLoadStates = providerLoadMetadata.providerLoadStates,
                  providersWithUnknownThreadCount = providerLoadMetadata.providersWithUnknownThreadCount,
                )
              }
            }
            stateStore.updateProject(normalizedEntryPath) { project ->
              val refreshedThreads = preserveThreadCosts(
                existingThreads = project.threads,
                newThreads = archiveSuppressionSupport.apply(normalizedEntryPath, finalResult.threads),
              )
              project.copy(
                threads = refreshedThreads,
                errorMessage = finalResult.errorMessage,
                providerWarnings = finalResult.providerWarnings,
                providerLoadStates = finalResult.providerLoadStates,
                providersWithUnknownThreadCount = finalResult.providersWithUnknownThreadCount,
              )
            }
            contentRepository.syncWarmSnapshotFromRuntime(normalizedEntryPath)
          }
          for ((worktreePath, _, _, worktreeProject) in worktreeEntries) {
            launch {
              val normalizedEntryPath = normalizeAgentWorkbenchPath(entryPath)
              val normalizedWorktreePath = normalizeAgentWorkbenchPath(worktreePath)
              val shouldLoadWorktree = worktreeProject != null && normalizedWorktreePath in bootstrap.loadPaths
              if (!shouldLoadWorktree) {
                stateStore.updateWorktree(normalizedEntryPath, normalizedWorktreePath) { worktree ->
                  worktree.withLoadingProvidersFailed()
                }
                return@launch
              }
              val finalResult = threadLoadSupport.loadSourcesIncrementally(
                sessionSources = availableSessionSources,
                normalizedPath = normalizedWorktreePath,
                project = worktreeProject,
                prefetchedByProvider = prefetchedByProvider,
                originalPath = worktreePath,
                cliAvailabilityByProvider = cliAvailabilityByProvider,
              ) { partial, _ ->
                stateStore.updateWorktree(normalizedEntryPath, normalizedWorktreePath) { worktree ->
                  val refreshedThreads = preserveThreadCosts(
                    existingThreads = worktree.threads,
                    newThreads = archiveSuppressionSupport.apply(normalizedWorktreePath, partial.threads),
                  )
                  val providerLoadMetadata = mergeProviderLoadMetadata(
                    currentProviderLoadStates = worktree.providerLoadStates,
                    currentProvidersWithUnknownThreadCount = worktree.providersWithUnknownThreadCount,
                    providerLoadStateUpdates = partial.providerLoadStates,
                    updatedProvidersWithUnknownThreadCount = partial.providersWithUnknownThreadCount,
                  )
                  worktree.copy(
                    threads = refreshedThreads,
                    providerWarnings = partial.providerWarnings,
                    providerLoadStates = providerLoadMetadata.providerLoadStates,
                    providersWithUnknownThreadCount = providerLoadMetadata.providersWithUnknownThreadCount,
                  )
                }
              }
              stateStore.updateWorktree(normalizedEntryPath, normalizedWorktreePath) { worktree ->
                val refreshedThreads = preserveThreadCosts(
                  existingThreads = worktree.threads,
                  newThreads = archiveSuppressionSupport.apply(normalizedWorktreePath, finalResult.threads),
                )
                worktree.copy(
                  threads = refreshedThreads,
                  errorMessage = finalResult.errorMessage,
                  providerWarnings = finalResult.providerWarnings,
                  providerLoadStates = finalResult.providerLoadStates,
                  providersWithUnknownThreadCount = finalResult.providersWithUnknownThreadCount,
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

  private suspend fun resolveCliAvailabilityByProvider(
    sessionSources: List<AgentSessionSource>,
  ): Map<AgentSessionProvider, Boolean> {
    return kotlinx.coroutines.withContext(Dispatchers.IO) {
      coroutineScope {
        sessionSources.map { source ->
          async {
            val descriptor = providerDescriptorProvider(source.provider) ?: return@async source.provider to true
            source.provider to try {
              AgentSessionProviderCliAvailabilityCache.resolveAvailability(descriptor, force = false) {
                descriptor.isCliAvailable()
              }
            }
            catch (e: CancellationException) {
              throw e
            }
            catch (t: Throwable) {
              LOG.warn("Failed to resolve CLI availability for ${source.provider.value}", t)
              false
            }
          }
        }.awaitAll().toMap()
      }
    }
  }

  private fun markProviderLoadStatesLoading(
    bootstrap: RefreshBootstrap,
    providerLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState>,
  ) {
    if (bootstrap.loadPaths.isEmpty() || providerLoadStates.isEmpty()) {
      return
    }
    stateStore.update { state ->
      var changed = false
      val nextProjects = state.projects.map { project ->
        val updatedProject = if (project.path in bootstrap.loadPaths) {
          val providerLoadMetadata = mergeProviderLoadMetadata(
            currentProviderLoadStates = project.providerLoadStates,
            currentProvidersWithUnknownThreadCount = project.providersWithUnknownThreadCount,
            providerLoadStateUpdates = providerLoadStates,
            updatedProvidersWithUnknownThreadCount = emptySet(),
          )
          val updated = project.copy(
            providerLoadStates = providerLoadMetadata.providerLoadStates,
            providersWithUnknownThreadCount = providerLoadMetadata.providersWithUnknownThreadCount,
          )
          if (updated != project) {
            changed = true
          }
          updated
        }
        else {
          project
        }

        val nextWorktrees = updatedProject.worktrees.map { worktree ->
          if (worktree.path !in bootstrap.loadPaths) {
            return@map worktree
          }
          val providerLoadMetadata = mergeProviderLoadMetadata(
            currentProviderLoadStates = worktree.providerLoadStates,
            currentProvidersWithUnknownThreadCount = worktree.providersWithUnknownThreadCount,
            providerLoadStateUpdates = providerLoadStates,
            updatedProvidersWithUnknownThreadCount = emptySet(),
          )
          val updated = worktree.copy(
            providerLoadStates = providerLoadMetadata.providerLoadStates,
            providersWithUnknownThreadCount = providerLoadMetadata.providersWithUnknownThreadCount,
          )
          if (updated != worktree) {
            changed = true
          }
          updated
        }
        if (nextWorktrees == updatedProject.worktrees) updatedProject else updatedProject.copy(worktrees = nextWorktrees)
      }
      if (changed) state.copy(projects = nextProjects, lastUpdatedAt = System.currentTimeMillis()) else state
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
          project.copy(
            providerWarnings = mergeProviderWarning(project.providerWarnings, warning),
            providerLoadStates = project.providerLoadStates + (provider to AgentSessionProviderLoadState.FAILED),
            providersWithUnknownThreadCount = project.providersWithUnknownThreadCount - provider,
          )
        }
        else {
          val nextWorktrees = project.worktrees.map { worktree ->
            if (worktree.path == path) {
              updated = true
              worktree.copy(
                providerWarnings = mergeProviderWarning(worktree.providerWarnings, warning),
                providerLoadStates = worktree.providerLoadStates + (provider to AgentSessionProviderLoadState.FAILED),
                providersWithUnknownThreadCount = worktree.providersWithUnknownThreadCount - provider,
              )
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

private fun AgentProjectSessions.withLoadingProvidersFailed(): AgentProjectSessions {
  val providerLoadMetadata = failLoadingProviderLoadMetadata(
    providerLoadStates = providerLoadStates,
    providersWithUnknownThreadCount = providersWithUnknownThreadCount,
  )
  if (providerLoadMetadata.providerLoadStates == this.providerLoadStates &&
      providerLoadMetadata.providersWithUnknownThreadCount == this.providersWithUnknownThreadCount) {
    return this
  }
  return copy(
    providerLoadStates = providerLoadMetadata.providerLoadStates,
    providersWithUnknownThreadCount = providerLoadMetadata.providersWithUnknownThreadCount,
  )
}

private fun AgentWorktree.withLoadingProvidersFailed(): AgentWorktree {
  val providerLoadMetadata = failLoadingProviderLoadMetadata(
    providerLoadStates = providerLoadStates,
    providersWithUnknownThreadCount = providersWithUnknownThreadCount,
  )
  if (providerLoadMetadata.providerLoadStates == this.providerLoadStates &&
      providerLoadMetadata.providersWithUnknownThreadCount == this.providersWithUnknownThreadCount) {
    return this
  }
  return copy(
    providerLoadStates = providerLoadMetadata.providerLoadStates,
    providersWithUnknownThreadCount = providerLoadMetadata.providersWithUnknownThreadCount,
  )
}

private data class PresentationHintStateUpdate(
  @JvmField val state: AgentSessionsState,
  @JvmField val presentationUpdates: List<AgentSessionThreadPresentationPatchUpdate>,
)

private data class PresentationHintThreadUpdate(
  @JvmField val threads: List<AgentSessionThread>,
  @JvmField val presentationUpdates: List<AgentSessionThreadPresentationPatchUpdate>,
)

private fun applyThreadPresentationHints(
  state: AgentSessionsState,
  provider: AgentSessionProvider,
  normalizedScopedPaths: Set<String>?,
  presentationUpdatesByThreadId: Map<String, AgentSessionThreadPresentationUpdate>,
): PresentationHintStateUpdate {
  val presentationUpdates = ArrayList<AgentSessionThreadPresentationPatchUpdate>()
  var changed = false
  val nextProjects = state.projects.map { project ->
    val projectUpdate = applyThreadPresentationHintsForPath(
      path = project.path,
      provider = provider,
      threads = project.threads,
      normalizedScopedPaths = normalizedScopedPaths,
      presentationUpdatesByThreadId = presentationUpdatesByThreadId,
    )
    presentationUpdates.addAll(projectUpdate.presentationUpdates)

    var worktreesChanged = false
    val nextWorktrees = project.worktrees.map { worktree ->
      val worktreeUpdate = applyThreadPresentationHintsForPath(
        path = worktree.path,
        provider = provider,
        threads = worktree.threads,
        normalizedScopedPaths = normalizedScopedPaths,
        presentationUpdatesByThreadId = presentationUpdatesByThreadId,
      )
      presentationUpdates.addAll(worktreeUpdate.presentationUpdates)
      if (worktreeUpdate.threads === worktree.threads) {
        worktree
      }
      else {
        worktreesChanged = true
        worktree.copy(threads = worktreeUpdate.threads)
      }
    }

    if (projectUpdate.threads === project.threads && !worktreesChanged) {
      project
    }
    else {
      changed = true
      project.copy(
        threads = projectUpdate.threads,
        worktrees = nextWorktrees,
      )
    }
  }

  if (!changed) {
    return PresentationHintStateUpdate(state = state, presentationUpdates = presentationUpdates)
  }
  return PresentationHintStateUpdate(
    state = state.copy(
      projects = nextProjects,
      lastUpdatedAt = System.currentTimeMillis(),
    ),
    presentationUpdates = presentationUpdates,
  )
}

private fun applyThreadPresentationHintsForPath(
  path: String,
  provider: AgentSessionProvider,
  threads: List<AgentSessionThread>,
  normalizedScopedPaths: Set<String>?,
  presentationUpdatesByThreadId: Map<String, AgentSessionThreadPresentationUpdate>,
): PresentationHintThreadUpdate {
  val normalizedPath = normalizeAgentWorkbenchPath(path)
  if (normalizedScopedPaths != null && normalizedPath !in normalizedScopedPaths) {
    return PresentationHintThreadUpdate(threads = threads, presentationUpdates = emptyList())
  }

  val presentationUpdates = ArrayList<AgentSessionThreadPresentationPatchUpdate>()
  var changed = false
  val nextThreads = threads.map { thread ->
    if (thread.provider != provider) {
      return@map thread
    }
    val presentationUpdate = presentationUpdatesByThreadId[thread.id] ?: return@map thread
    val resolvedUpdate = resolveAgentThreadPresentationUpdate(
      thread = thread,
      presentationUpdate = presentationUpdate,
    )
    if (resolvedUpdate.title == thread.title && resolvedUpdate.activityReport == thread.activityReport && resolvedUpdate.updatedAt == thread.updatedAt) {
      return@map thread
    }
    LOG.debug {
      "Applying ${provider.value} presentation hint path=$path threadId=${thread.id} " +
      "titleChanged=${resolvedUpdate.title != thread.title} " +
      "activity=${thread.activity}->${resolvedUpdate.activityReport.rowActivity} " +
      "summaryActivity=${thread.summaryActivity}->${resolvedUpdate.activityReport.chromeActivity} " +
      "updatedAt=${thread.updatedAt}->${resolvedUpdate.updatedAt}"
    }
    presentationUpdates += AgentSessionThreadPresentationPatchUpdate(
      path = path,
      threadId = thread.id,
      title = resolvedUpdate.title,
      activityReport = resolvedUpdate.activityReport,
      updatedAt = resolvedUpdate.updatedAt,
    )
    changed = true
    thread.copy(title = resolvedUpdate.title, activityReport = resolvedUpdate.activityReport, updatedAt = resolvedUpdate.updatedAt)
  }

  if (!changed) {
    return PresentationHintThreadUpdate(threads = threads, presentationUpdates = presentationUpdates)
  }
  return PresentationHintThreadUpdate(
    threads = nextThreads,
    presentationUpdates = presentationUpdates,
  )
}

private fun Set<String>?.debugSizeText(): String {
  return this?.size?.toString() ?: "all"
}

private fun collectVfsRefreshCandidatePaths(
  state: AgentSessionsState,
  provider: AgentSessionProvider,
  updateEvent: AgentSessionSourceUpdateEvent,
): Set<String> {
  if (updateEvent.isUnscoped()) {
    return collectOpenOrLoadedPaths(state)
  }

  val candidatePaths = LinkedHashSet<String>()
  updateEvent.scopedPaths?.let { scopedPaths ->
    candidatePaths.addAll(scopedPaths)
    candidatePaths.addAll(resolveKnownPathsFromScopedPaths(state = state, scopedPaths = scopedPaths))
  }
  updateEvent.threadIds?.let { threadIds ->
    candidatePaths.addAll(resolvePathsForVfsRefreshThreadIds(state = state, provider = provider, threadIds = threadIds))
  }
  return candidatePaths
}

private fun collectExactVfsRefreshPaths(
  ownerRootPaths: Set<String>,
  changedProjectFilePaths: Set<String>,
  isOwnerRootRefreshEnabled: (String) -> Boolean,
): ExactVfsRefreshPaths {
  val ownerRoots = ownerRootPaths.mapNotNull(::toVfsRefreshOwnerRoot)
  if (ownerRoots.isEmpty()) {
    return ExactVfsRefreshPaths(paths = emptySet(), skippedOutsideRoot = changedProjectFilePaths.size, disabledRoots = 0)
  }

  val paths = LinkedHashSet<String>()
  var skippedOutsideRoot = 0
  var disabledRoots = 0
  for (changedProjectFilePath in changedProjectFilePaths) {
    val changedPath = parseAgentWorkbenchPathOrNull(changedProjectFilePath)?.takeIf { it.isAbsolute }?.normalize()
    if (changedPath == null) {
      skippedOutsideRoot++
      continue
    }
    val ownerRoot = ownerRoots
      .asSequence()
      .filter { root -> changedPath.startsWith(root.path) }
      .maxByOrNull { root -> root.path.nameCount }
    if (ownerRoot == null) {
      skippedOutsideRoot++
      continue
    }
    if (!isOwnerRootRefreshEnabled(ownerRoot.originalPath)) {
      disabledRoots++
      continue
    }
    resolveExistingRefreshPath(changedPath = changedPath, ownerRootPath = ownerRoot.path)?.let(paths::add)
  }
  return ExactVfsRefreshPaths(paths = paths, skippedOutsideRoot = skippedOutsideRoot, disabledRoots = disabledRoots)
}

private fun toVfsRefreshOwnerRoot(path: String): VfsRefreshOwnerRoot? {
  val parsedPath = parseAgentWorkbenchPathOrNull(path)?.takeIf { it.isAbsolute }?.normalize() ?: return null
  return VfsRefreshOwnerRoot(originalPath = path, path = parsedPath)
}

private fun resolveExistingRefreshPath(changedPath: Path, ownerRootPath: Path): String? {
  var current: Path? = changedPath
  while (current != null && current.startsWith(ownerRootPath)) {
    if (Files.exists(current)) {
      return normalizeAgentWorkbenchPath(current.toString())
    }
    current = current.parent
  }
  return null
}

private data class VfsRefreshOwnerRoot(
  @JvmField val originalPath: String,
  @JvmField val path: Path,
)

private data class ExactVfsRefreshPaths(
  @JvmField val paths: Set<String>,
  @JvmField val skippedOutsideRoot: Int,
  @JvmField val disabledRoots: Int,
)

private fun collectOpenOrLoadedPaths(state: AgentSessionsState): Set<String> {
  val paths = LinkedHashSet<String>()
  state.forEachPathContent { content ->
    if (content.isOpen || content.hasAnyProviderSnapshot) {
      paths.add(content.path)
    }
  }
  return paths
}

private fun resolveKnownPathsFromScopedPaths(state: AgentSessionsState, scopedPaths: Set<String>): Set<String> {
  if (scopedPaths.isEmpty()) {
    return emptySet()
  }
  val normalizedScopedPaths = scopedPaths.mapTo(LinkedHashSet()) { path -> normalizeAgentWorkbenchPath(path) }
  return collectOpenOrLoadedPaths(state)
    .asSequence()
    .filter { path -> normalizeAgentWorkbenchPath(path) in normalizedScopedPaths }
    .toCollection(LinkedHashSet())
}

private fun resolvePathsForVfsRefreshThreadIds(
  state: AgentSessionsState,
  provider: AgentSessionProvider,
  threadIds: Set<String>,
): Set<String> {
  if (threadIds.isEmpty()) {
    return emptySet()
  }

  val paths = LinkedHashSet<String>()
  state.forEachPathContent { content ->
    if (content.threads.any { thread -> thread.matchesProviderAndThreadIds(provider, threadIds) }) {
      paths.add(content.path)
    }
  }
  return paths
}

private fun AgentSessionThread.matchesProviderAndThreadIds(provider: AgentSessionProvider, threadIds: Set<String>): Boolean {
  return this.provider == provider && (id in threadIds || subAgents.any { subAgent -> subAgent.id in threadIds })
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
