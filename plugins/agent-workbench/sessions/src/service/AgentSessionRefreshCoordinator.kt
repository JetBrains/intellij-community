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
import com.intellij.agent.workbench.chat.updateOpenAgentChatTabPresentation
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.config.AgentWorkbenchProjectRuntimeConfigs
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.isUnscoped
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderWarning
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.model.ProjectEntry
import com.intellij.agent.workbench.sessions.settings.AgentSessionProviderSettingsService
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.state.AgentSessionThreadTitleOverrides
import com.intellij.agent.workbench.sessions.state.InMemoryAgentSessionThreadTitleOverrides
import com.intellij.agent.workbench.sessions.util.agentSessionCliMissingMessageKey
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val LOG = logger<AgentSessionRefreshCoordinator>()

internal class AgentSessionRefreshCoordinator(
  private val serviceScope: CoroutineScope,
  private val sessionSourcesProvider: () -> List<AgentSessionSource>,
  private val projectEntriesProvider: suspend () -> List<ProjectEntry>,
  private val stateStore: AgentSessionsStateStore,
  private val contentRepository: AgentSessionContentRepository,
  private val isRefreshGateActive: suspend () -> Boolean,
  private val titleOverrides: AgentSessionThreadTitleOverrides = InMemoryAgentSessionThreadTitleOverrides(),
  private val scheduleVfsRefresh: (Set<String>) -> Unit = ::scheduleAgentWorkbenchVfsRefresh,
  private val isVfsRefreshOnStatusUpdatesEnabled: (String) -> Boolean =
    AgentWorkbenchProjectRuntimeConfigs::isRefreshVfsOnStatusUpdatesEnabled,
  private val openAgentChatSnapshotProvider: suspend () -> AgentChatOpenTabsRefreshSnapshot = ::collectOpenAgentChatRefreshSnapshot,
  private val providerDescriptorsByIdProvider: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProvidersById,
  private val providerDescriptorProvider: (AgentSessionProvider) -> AgentSessionProviderDescriptor? = AgentSessionProviders::find,
  scopedRefreshSignalsProvider: (AgentSessionProvider) -> Flow<AgentSessionSourceUpdateEvent> = { provider ->
    agentChatScopedRefreshSignals(provider)
  },
  private val openAgentChatTabPresentationUpdater: suspend (
    AgentSessionProvider,
    Set<String>,
    Map<Pair<String, String>, String>,
    Map<Pair<String, String>, AgentThreadActivity>,
  ) -> Int = ::updateOpenAgentChatTabPresentation,
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
    titleOverrides = titleOverrides,
    resolveErrorMessage = ::resolveErrorMessage,
    resolveProviderWarningMessage = ::resolveProviderWarningMessage,
    providerDescriptorProvider = providerDescriptorProvider,
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
    titleOverrides = titleOverrides,
  )
  private val providerRefreshRunner = AgentSessionProviderRefreshRunner(
    refreshMutex = refreshMutex,
    sessionSourcesProvider = sessionSourcesProvider,
    stateStore = stateStore,
    contentRepository = contentRepository,
    archiveSuppressionSupport = archiveSuppressionSupport,
    titleOverrides = titleOverrides,
    refreshSupportProvider = ::refreshSupportFor,
    resolveProviderWarningMessage = ::resolveProviderWarningMessage,
    openAgentChatSnapshotProvider = openAgentChatSnapshotProvider,
    openAgentChatTabPresentationUpdater = openAgentChatTabPresentationUpdater,
  )
  private val refreshScheduler = AgentSessionRefreshScheduler(
    serviceScope = serviceScope,
    sessionSourcesProvider = sessionSourcesProvider,
    scopedRefreshProvidersProvider = {
      service<AgentSessionProviderSettingsService>().enabledProviders(providerDescriptorsByIdProvider())
        .asSequence()
        .filter { provider -> provider.emitsScopedRefreshSignals }
        .map { provider -> provider.provider }
        .toList()
    },
    scopedRefreshSignalsProvider = scopedRefreshSignalsProvider,
    isRefreshGateActive = isRefreshGateActive,
    executeFullRefresh = ::refreshNow,
    executeProviderRefresh = providerRefreshRunner::refreshLoadedProviderThreads,
    executeProviderHintRefresh = providerRefreshRunner::refreshLoadedProviderHints,
    applySourceUpdateActivityHints = ::applySourceUpdateActivityHints,
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
          openAgentChatPendingTabsBinder = openAgentChatPendingTabsBinder,
          clearOpenConcreteNewThreadRebindAnchors = clearOpenConcreteNewThreadRebindAnchors,
        )
      }
    }
  }

  private fun applySourceUpdateActivityHints(provider: AgentSessionProvider, updateEvent: AgentSessionSourceUpdateEvent) {
    val activityHintsByThreadId = updateEvent.activityHintsByThreadId
    if (activityHintsByThreadId.isEmpty()) {
      return
    }
    val summaryActivityHintsByThreadId = updateEvent.summaryActivityHintsByThreadId

    val normalizedScopedPaths = updateEvent.scopedPaths
      ?.asSequence()
      ?.map(::normalizeAgentWorkbenchPath)
      ?.filter(String::isNotBlank)
      ?.toCollection(LinkedHashSet())

    var activityByPathAndThreadIdentity: Map<Pair<String, String>, AgentThreadActivity> = emptyMap()
    stateStore.update { state ->
      val update = applyThreadActivityHints(
        state = state,
        provider = provider,
        normalizedScopedPaths = normalizedScopedPaths,
        activityHintsByThreadId = activityHintsByThreadId,
        summaryActivityHintsByThreadId = summaryActivityHintsByThreadId,
      )
      activityByPathAndThreadIdentity = update.activityByPathAndThreadIdentity
      update.state
    }

    if (activityByPathAndThreadIdentity.isEmpty()) {
      return
    }
    serviceScope.launch {
      openAgentChatTabPresentationUpdater(
        provider,
        emptySet(),
        emptyMap(),
        activityByPathAndThreadIdentity,
      )
    }
  }

  private fun scheduleVfsRefreshForSourceUpdate(provider: AgentSessionProvider, updateEvent: AgentSessionSourceUpdateEvent) {
    if (!updateEvent.mayHaveChangedProjectFiles) {
      LOG.debug {
        "Skipped VFS refresh for ${provider.value} source update: no project file change evidence"
      }
      return
    }
    val candidatePaths = collectVfsRefreshCandidatePaths(
      state = stateStore.snapshot(),
      provider = provider,
      updateEvent = updateEvent,
    )
    if (candidatePaths.isEmpty()) {
      LOG.debug {
        "Skipped VFS refresh for ${provider.value} source update: no resolved project paths"
      }
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
      if (bootstrap.knownPaths.isNotEmpty()) {
        titleOverrides.retainPaths(bootstrap.knownPaths)
      }
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
              sessionSources = availableSessionSources,
              normalizedPath = normalizedEntryPath,
              project = entryProject,
              prefetchedByProvider = prefetchedByProvider,
              originalPath = entry.path,
              cliAvailabilityByProvider = cliAvailabilityByProvider,
            ) { partial, isComplete ->
              stateStore.updateProject(normalizedEntryPath) { project ->
                val refreshedThreads = preserveThreadCosts(
                  existingThreads = project.threads,
                  newThreads = archiveSuppressionSupport.apply(normalizedEntryPath, partial.threads),
                )
                project.copy(
                  threads = refreshedThreads,
                  providerWarnings = partial.providerWarnings,
                  isLoading = !isComplete,
                )
              }
            }
            stateStore.updateProject(normalizedEntryPath) { project ->
              val refreshedThreads = preserveThreadCosts(
                existingThreads = project.threads,
                newThreads = archiveSuppressionSupport.apply(normalizedEntryPath, finalResult.threads),
              )
              project.copy(
                isLoading = false,
                hasLoaded = true,
                hasUnknownThreadCount = finalResult.hasUnknownThreadCount,
                threads = refreshedThreads,
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
                sessionSources = availableSessionSources,
                normalizedPath = normalizedWorktreePath,
                project = worktreeProject,
                prefetchedByProvider = prefetchedByProvider,
                originalPath = wt.path,
                cliAvailabilityByProvider = cliAvailabilityByProvider,
              ) { partial, isComplete ->
                stateStore.updateWorktree(normalizedEntryPath, normalizedWorktreePath) { worktree ->
                  val refreshedThreads = preserveThreadCosts(
                    existingThreads = worktree.threads,
                    newThreads = archiveSuppressionSupport.apply(normalizedWorktreePath, partial.threads),
                  )
                  worktree.copy(
                    threads = refreshedThreads,
                    providerWarnings = partial.providerWarnings,
                    isLoading = !isComplete,
                  )
                }
              }
              stateStore.updateWorktree(normalizedEntryPath, normalizedWorktreePath) { worktree ->
                val refreshedThreads = preserveThreadCosts(
                  existingThreads = worktree.threads,
                  newThreads = archiveSuppressionSupport.apply(normalizedWorktreePath, finalResult.threads),
                )
                worktree.copy(
                  isLoading = false,
                  hasLoaded = true,
                  hasUnknownThreadCount = finalResult.hasUnknownThreadCount,
                  threads = refreshedThreads,
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

  private suspend fun resolveCliAvailabilityByProvider(
    sessionSources: List<AgentSessionSource>,
  ): Map<AgentSessionProvider, Boolean> {
    return coroutineScope {
      sessionSources.map { source ->
        async {
          val descriptor = providerDescriptorProvider(source.provider) ?: return@async source.provider to true
          source.provider to try {
            descriptor.isCliAvailable()
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

private data class ActivityHintStateUpdate(
  @JvmField val state: AgentSessionsState,
  @JvmField val activityByPathAndThreadIdentity: Map<Pair<String, String>, AgentThreadActivity>,
)

private data class ActivityHintThreadUpdate(
  @JvmField val threads: List<AgentSessionThread>,
  @JvmField val activityByPathAndThreadIdentity: Map<Pair<String, String>, AgentThreadActivity>,
)

private fun applyThreadActivityHints(
  state: AgentSessionsState,
  provider: AgentSessionProvider,
  normalizedScopedPaths: Set<String>?,
  activityHintsByThreadId: Map<String, AgentThreadActivity>,
  summaryActivityHintsByThreadId: Map<String, AgentThreadActivity?>,
): ActivityHintStateUpdate {
  val activityByPathAndThreadIdentity = LinkedHashMap<Pair<String, String>, AgentThreadActivity>()
  var changed = false
  val nextProjects = state.projects.map { project ->
    val projectUpdate = applyThreadActivityHintsForPath(
      path = project.path,
      provider = provider,
      threads = project.threads,
      normalizedScopedPaths = normalizedScopedPaths,
      activityHintsByThreadId = activityHintsByThreadId,
      summaryActivityHintsByThreadId = summaryActivityHintsByThreadId,
    )
    activityByPathAndThreadIdentity.putAll(projectUpdate.activityByPathAndThreadIdentity)

    var worktreesChanged = false
    val nextWorktrees = project.worktrees.map { worktree ->
      val worktreeUpdate = applyThreadActivityHintsForPath(
        path = worktree.path,
        provider = provider,
        threads = worktree.threads,
        normalizedScopedPaths = normalizedScopedPaths,
        activityHintsByThreadId = activityHintsByThreadId,
        summaryActivityHintsByThreadId = summaryActivityHintsByThreadId,
      )
      activityByPathAndThreadIdentity.putAll(worktreeUpdate.activityByPathAndThreadIdentity)
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
    return ActivityHintStateUpdate(state = state, activityByPathAndThreadIdentity = activityByPathAndThreadIdentity)
  }
  return ActivityHintStateUpdate(
    state = state.copy(
      projects = nextProjects,
      lastUpdatedAt = System.currentTimeMillis(),
    ),
    activityByPathAndThreadIdentity = activityByPathAndThreadIdentity,
  )
}

private fun applyThreadActivityHintsForPath(
  path: String,
  provider: AgentSessionProvider,
  threads: List<AgentSessionThread>,
  normalizedScopedPaths: Set<String>?,
  activityHintsByThreadId: Map<String, AgentThreadActivity>,
  summaryActivityHintsByThreadId: Map<String, AgentThreadActivity?>,
): ActivityHintThreadUpdate {
  val normalizedPath = normalizeAgentWorkbenchPath(path)
  if (normalizedScopedPaths != null && normalizedPath !in normalizedScopedPaths) {
    return ActivityHintThreadUpdate(threads = threads, activityByPathAndThreadIdentity = emptyMap())
  }

  val activityByPathAndThreadIdentity = LinkedHashMap<Pair<String, String>, AgentThreadActivity>()
  var changed = false
  val nextThreads = threads.map { thread ->
    if (thread.provider != provider) {
      return@map thread
    }
    val hintedActivity = activityHintsByThreadId[thread.id] ?: return@map thread
    val hintedSummaryActivity = resolveHintedSummaryActivity(
      thread = thread,
      hintedActivity = hintedActivity,
      summaryActivityHintsByThreadId = summaryActivityHintsByThreadId,
    )
    if (hintedActivity == thread.activity && thread.summaryActivity == hintedSummaryActivity) {
      return@map thread
    }
    activityByPathAndThreadIdentity[path to buildAgentSessionIdentity(provider, thread.id)] = hintedActivity
    changed = true
    thread.copy(activity = hintedActivity, summaryActivity = hintedSummaryActivity)
  }

  if (!changed) {
    return ActivityHintThreadUpdate(threads = threads, activityByPathAndThreadIdentity = activityByPathAndThreadIdentity)
  }
  return ActivityHintThreadUpdate(
    threads = nextThreads,
    activityByPathAndThreadIdentity = activityByPathAndThreadIdentity,
  )
}

private fun resolveHintedSummaryActivity(
  thread: AgentSessionThread,
  hintedActivity: AgentThreadActivity,
  summaryActivityHintsByThreadId: Map<String, AgentThreadActivity?>,
): AgentThreadActivity? {
  return when {
    summaryActivityHintsByThreadId.containsKey(thread.id) -> summaryActivityHintsByThreadId[thread.id]
    thread.summaryActivity == null -> null
    else -> hintedActivity
  }
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

private fun collectOpenOrLoadedPaths(state: AgentSessionsState): Set<String> {
  val paths = LinkedHashSet<String>()
  for (project in state.projects) {
    if (project.isOpen || project.hasLoaded) {
      paths.add(project.path)
    }
    for (worktree in project.worktrees) {
      if (worktree.isOpen || worktree.hasLoaded) {
        paths.add(worktree.path)
      }
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
  for (project in state.projects) {
    if (project.threads.any { thread -> thread.matchesProviderAndThreadIds(provider, threadIds) }) {
      paths.add(project.path)
    }
    for (worktree in project.worktrees) {
      if (worktree.threads.any { thread -> thread.matchesProviderAndThreadIds(provider, threadIds) }) {
        paths.add(worktree.path)
      }
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
