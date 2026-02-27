// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindTarget
import com.intellij.agent.workbench.chat.collectOpenAgentChatProjectPaths
import com.intellij.agent.workbench.chat.collectOpenConcreteAgentChatThreadIdentitiesByPath
import com.intellij.agent.workbench.chat.collectOpenPendingAgentChatProjectPaths
import com.intellij.agent.workbench.chat.collectOpenPendingCodexTabsByPath
import com.intellij.agent.workbench.chat.rebindSpecificOpenPendingCodexTab
import com.intellij.agent.workbench.chat.updateOpenAgentChatTabPresentation
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.openapi.application.UI
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<AgentSessionsLoadingCoordinator>()
private const val SOURCE_UPDATE_DEBOUNCE_MS = 350L
private const val SOURCE_REFRESH_GATE_RETRY_MS = 500L
private const val PENDING_CODEX_REBIND_POLL_INTERVAL_MS = 1_500L
private const val PENDING_CODEX_MATCH_PRE_WINDOW_MS = 20_000L
private const val PENDING_CODEX_MATCH_POST_WINDOW_MS = 120_000L
private const val PENDING_CODEX_AMBIGUITY_NOTIFY_AFTER_POLLS = 2
private const val PENDING_CODEX_AMBIGUITY_NOTIFY_COOLDOWN_MS = 5 * 60 * 1000L

internal class AgentSessionsLoadingCoordinator(
  private val serviceScope: CoroutineScope,
  private val sessionSourcesProvider: () -> List<AgentSessionSource>,
  private val projectEntriesProvider: suspend () -> List<ProjectEntry>,
  private val treeUiState: SessionsTreeUiState,
  private val stateStore: AgentSessionsStateStore,
  private val isRefreshGateActive: suspend () -> Boolean,
  private val openAgentChatProjectPathsProvider: suspend () -> Set<String> = ::collectOpenAgentChatProjectPaths,
  private val openPendingAgentChatProjectPathsProvider: suspend () -> Set<String> = ::collectOpenPendingAgentChatProjectPaths,
  private val openPendingCodexTabsProvider: suspend () -> Map<String, List<AgentChatPendingCodexTabSnapshot>> = ::collectOpenPendingCodexTabsByPath,
  private val openConcreteChatThreadIdentitiesByPathProvider: suspend () -> Map<String, Set<String>> = ::collectOpenConcreteAgentChatThreadIdentitiesByPath,
  private val openAgentChatTabPresentationUpdater: suspend (
    Map<Pair<String, String>, String>,
    Map<Pair<String, String>, AgentThreadActivity>,
  ) -> Int = ::updateOpenAgentChatTabPresentation,
  private val openAgentChatPendingTabSpecificBinder: (
    String,
    String,
    AgentChatPendingTabRebindTarget,
  ) -> Boolean = ::rebindSpecificOpenPendingCodexTab,
  private val pendingCodexRebindPollIntervalMs: Long = PENDING_CODEX_REBIND_POLL_INTERVAL_MS,
) {
  private val refreshMutex = Mutex()
  private val refreshQueueLock = Any()
  private var pendingRefreshRequest: RefreshRequestType? = null
  private var refreshProcessorRunning = false
  private val onDemandMutex = Mutex()
  private val onDemandLoading = LinkedHashSet<String>()
  private val onDemandWorktreeLoading = LinkedHashSet<String>()
  private val sourceRefreshJobs = LinkedHashMap<AgentSessionProvider, Job>()
  private val sourceRefreshJobsLock = Any()
  private val pendingSourceRefreshProviders = LinkedHashMap<AgentSessionProvider, Set<String>?>()
  private var sourceRefreshProcessorRunning = false
  private val sourceRefreshIdCounter = AtomicLong()
  private val sourceObserverJobs = LinkedHashMap<AgentSessionProvider, Job>()
  private val sourceObserverJobsLock = Any()
  private val archiveSuppressions = LinkedHashSet<ArchiveSuppression>()
  private val archiveSuppressionsLock = Any()
  private val pendingCodexRebindPollLock = Any()
  private var pendingCodexRebindPollJob: Job? = null
  private val pendingCodexAmbiguityLock = Any()
  private val pendingCodexAmbiguityStateByKey = LinkedHashMap<String, PendingCodexAmbiguityState>()

  fun observeSessionSourceUpdates() {
    ensureSourceUpdateObservers()
    ensurePendingCodexRebindPolling()
  }

  private fun ensurePendingCodexRebindPolling() {
    synchronized(pendingCodexRebindPollLock) {
      val existing = pendingCodexRebindPollJob
      if (existing != null && existing.isActive) {
        return
      }

      val pollIntervalMs = pendingCodexRebindPollIntervalMs.coerceAtLeast(200L)
      pendingCodexRebindPollJob = serviceScope.launch(Dispatchers.IO) {
        while (true) {
          delay(pollIntervalMs.milliseconds)
          val pendingPaths = try {
            openPendingAgentChatProjectPathsProvider()
          }
          catch (e: Throwable) {
            if (e is CancellationException) throw e
            LOG.warn("Failed to collect pending chat paths for Codex refresh polling", e)
            emptySet()
          }
          if (pendingPaths.isEmpty()) {
            continue
          }

          LOG.debug {
            "Detected pending Codex chat tabs (paths=${pendingPaths.size}); scheduling scoped provider refresh"
          }
          enqueueSourceRefresh(AgentSessionProvider.CODEX, scopedPaths = pendingPaths)
        }
      }
    }
  }

  fun refresh() {
    enqueueRefresh(RefreshRequestType.FULL_REFRESH)
  }

  fun refreshCatalogAndLoadNewlyOpened() {
    enqueueRefresh(RefreshRequestType.CATALOG_SYNC)
  }

  internal fun refreshProviderScope(provider: AgentSessionProvider, scopedPaths: Set<String>) {
    enqueueSourceRefresh(provider = provider, scopedPaths = scopedPaths)
  }

  private fun enqueueRefresh(requestType: RefreshRequestType) {
    var shouldStartProcessor = false
    synchronized(refreshQueueLock) {
      pendingRefreshRequest = mergeRefreshRequestTypes(pendingRefreshRequest, requestType)
      if (!refreshProcessorRunning) {
        refreshProcessorRunning = true
        shouldStartProcessor = true
      }
    }
    if (!shouldStartProcessor) {
      return
    }
    serviceScope.launch(Dispatchers.IO) {
      processQueuedRefreshRequests()
    }
  }

  private suspend fun processQueuedRefreshRequests() {
    while (true) {
      val requestType = synchronized(refreshQueueLock) {
        val next = pendingRefreshRequest
        if (next == null) {
          refreshProcessorRunning = false
          null
        }
        else {
          pendingRefreshRequest = null
          next
        }
      } ?: return

      try {
        executeRefreshRequest(requestType)
      }
      catch (e: Throwable) {
        if (e is CancellationException) throw e
        LOG.error("Failed to load agent sessions", e)
        stateStore.markLoadFailure(AgentSessionsBundle.message("toolwindow.error"))
      }
    }
  }

  private suspend fun executeRefreshRequest(requestType: RefreshRequestType) {
    refreshMutex.withLock {
      val loadScope = when (requestType) {
        RefreshRequestType.FULL_REFRESH -> OpenProjectLoadScope.ALL_OPEN_PROJECTS
        RefreshRequestType.CATALOG_SYNC -> OpenProjectLoadScope.NEWLY_OPENED_ONLY
      }
      refreshNow(loadScope)
    }
  }

  private suspend fun refreshNow(loadScope: OpenProjectLoadScope) {
    ensureSourceUpdateObservers()
    val currentState = stateStore.snapshot()
    val bootstrap = buildRefreshBootstrap(currentState = currentState, loadScope = loadScope)
    treeUiState.retainOpenProjectThreadPreviews(bootstrap.openPaths)
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
            val finalResult = loadSourcesIncrementally(
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
          if (finalResult.errorMessage == null) {
            treeUiState.setOpenProjectThreadPreviews(normalizedEntryPath, finalResult.threads.toThreadPreviews())
          }
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
            val finalResult = loadSourcesIncrementally(
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
            if (finalResult.errorMessage == null) {
              treeUiState.setOpenProjectThreadPreviews(normalizedWorktreePath, finalResult.threads.toThreadPreviews())
            }
          }
        }
      }
    }
    stateStore.update { it.copy(lastUpdatedAt = System.currentTimeMillis()) }
  }

  private suspend fun buildRefreshBootstrap(
    currentState: AgentSessionsState,
    loadScope: OpenProjectLoadScope,
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
      val cachedPreviews = if (entryIsOpen) {
        treeUiState.getOpenProjectThreadPreviews(normalizedEntryPath)
      }
      else {
        null
      }
      val cachedThreads = cachedPreviews.orEmpty().toCachedSessionThreads()
      AgentProjectSessions(
        path = normalizedEntryPath,
        name = entry.name,
        branch = entry.branch ?: existing?.branch,
        isOpen = entryIsOpen,
        isLoading = shouldLoadProject,
        hasLoaded = existing?.hasLoaded ?: (cachedPreviews != null),
        hasUnknownThreadCount = existing?.hasUnknownThreadCount ?: false,
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
          val cachedWorktreePreviews = if (worktreeIsOpen) {
            treeUiState.getOpenProjectThreadPreviews(normalizedWorktreePath)
          }
          else {
            null
          }
          val cachedWorktreeThreads = cachedWorktreePreviews.orEmpty().toCachedSessionThreads()
          AgentWorktree(
            path = normalizedWorktreePath,
            name = wt.name,
            branch = wt.branch,
            isOpen = worktreeIsOpen,
            isLoading = shouldLoadWorktree,
            hasLoaded = existingWt?.hasLoaded ?: (cachedWorktreePreviews != null),
            hasUnknownThreadCount = existingWt?.hasUnknownThreadCount ?: false,
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
    loadScope: OpenProjectLoadScope,
    openPaths: MutableSet<String>,
    loadPaths: MutableSet<String>,
  ): Boolean {
    if (!isOpen) {
      return false
    }
    openPaths.add(normalizedPath)
    val shouldLoad = when (loadScope) {
      OpenProjectLoadScope.ALL_OPEN_PROJECTS -> true
      OpenProjectLoadScope.NEWLY_OPENED_ONLY -> !wasOpen
    }
    if (shouldLoad) {
      loadPaths.add(normalizedPath)
    }
    return shouldLoad
  }

  private fun mergeRefreshRequestTypes(
    current: RefreshRequestType?,
    incoming: RefreshRequestType,
  ): RefreshRequestType {
    if (current == null) {
      return incoming
    }
    return if (current == RefreshRequestType.FULL_REFRESH || incoming == RefreshRequestType.FULL_REFRESH) {
      RefreshRequestType.FULL_REFRESH
    }
    else {
      RefreshRequestType.CATALOG_SYNC
    }
  }

  private enum class RefreshRequestType {
    CATALOG_SYNC,
    FULL_REFRESH,
  }

  private enum class OpenProjectLoadScope {
    NEWLY_OPENED_ONLY,
    ALL_OPEN_PROJECTS,
  }

  private data class RefreshBootstrap(
    val entries: List<ProjectEntry>,
    val openPaths: Set<String>,
    val loadPaths: Set<String>,
    val initialProjects: List<AgentProjectSessions>,
    val initialVisibleThreadCounts: Map<String, Int>,
  )

  private fun ensureSourceUpdateObservers() {
    val availableSources = LinkedHashMap<AgentSessionProvider, AgentSessionSource>()
    for (source in sessionSourcesProvider()) {
      if (availableSources.putIfAbsent(source.provider, source) != null) {
        LOG.warn("Duplicate session source for provider ${source.provider.value}; ignoring ${source::class.java.name}")
      }
    }

    synchronized(sourceObserverJobsLock) {
      val jobIterator = sourceObserverJobs.entries.iterator()
      while (jobIterator.hasNext()) {
        val (provider, job) = jobIterator.next()
        val source = availableSources[provider]
        if (source != null && source.supportsUpdates) continue
        LOG.debug { "Stopping source updates observer for ${provider.value}" }
        job.cancel()
        jobIterator.remove()
      }

      for ((provider, source) in availableSources) {
        if (!source.supportsUpdates) continue
        if (sourceObserverJobs.containsKey(provider)) continue

        LOG.debug { "Starting source updates observer for ${provider.value}" }
        val job = serviceScope.launch(Dispatchers.IO) {
          try {
            source.updates.collect {
              scheduleSourceRefresh(provider)
            }
          }
          catch (e: Throwable) {
            if (e is CancellationException) throw e
            LOG.warn("Source updates observer failed for ${provider.value}", e)
          }
        }
        sourceObserverJobs[provider] = job
        job.invokeOnCompletion {
          synchronized(sourceObserverJobsLock) {
            if (sourceObserverJobs[provider] === job) {
              sourceObserverJobs.remove(provider)
            }
          }
        }
      }
    }
  }

  fun suppressArchivedThread(path: String, provider: AgentSessionProvider, threadId: String) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    synchronized(archiveSuppressionsLock) {
      archiveSuppressions.add(ArchiveSuppression(path = normalizedPath, provider = provider, threadId = threadId))
    }
  }

  fun unsuppressArchivedThread(path: String, provider: AgentSessionProvider, threadId: String) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    synchronized(archiveSuppressionsLock) {
      archiveSuppressions.remove(ArchiveSuppression(path = normalizedPath, provider = provider, threadId = threadId))
    }
  }

  fun loadProjectThreadsOnDemand(path: String) {
    serviceScope.launch(Dispatchers.IO) {
      val normalized = normalizeAgentWorkbenchPath(path)
      if (!markOnDemandLoading(normalized)) return@launch
      try {
        stateStore.updateProject(normalized) { project ->
          project.copy(
            isLoading = true,
            hasUnknownThreadCount = false,
            errorMessage = null,
            providerWarnings = emptyList(),
          )
        }
        val result = loadThreadsFromClosedProject(path = normalized)
        stateStore.updateProject(normalized) { project ->
          project.copy(
            isLoading = false,
            hasLoaded = true,
            hasUnknownThreadCount = result.hasUnknownThreadCount,
            threads = result.threads,
            errorMessage = result.errorMessage,
            providerWarnings = result.providerWarnings,
          )
        }
      }
      finally {
        clearOnDemandLoading(normalized)
      }
    }
  }

  fun loadWorktreeThreadsOnDemand(projectPath: String, worktreePath: String) {
    serviceScope.launch(Dispatchers.IO) {
      val normalizedProject = normalizeAgentWorkbenchPath(projectPath)
      val normalizedWorktree = normalizeAgentWorkbenchPath(worktreePath)
      if (!markWorktreeOnDemandLoading(normalizedProject, normalizedWorktree)) return@launch
      try {
        stateStore.updateWorktree(normalizedProject, normalizedWorktree) { worktree ->
          worktree.copy(
            isLoading = true,
            hasUnknownThreadCount = false,
            errorMessage = null,
            providerWarnings = emptyList(),
          )
        }
        val result = loadThreadsFromClosedProject(path = normalizedWorktree)
        stateStore.updateWorktree(normalizedProject, normalizedWorktree) { worktree ->
          worktree.copy(
            isLoading = false,
            hasLoaded = true,
            hasUnknownThreadCount = result.hasUnknownThreadCount,
            threads = result.threads,
            errorMessage = result.errorMessage,
            providerWarnings = result.providerWarnings,
          )
        }
      }
      finally {
        clearWorktreeOnDemandLoading(normalizedWorktree)
      }
    }
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

  private fun scheduleSourceRefresh(provider: AgentSessionProvider) {
    synchronized(sourceRefreshJobsLock) {
      sourceRefreshJobs.remove(provider)?.cancel()
      LOG.debug { "Scheduled debounced source refresh for ${provider.value}" }
      val job = serviceScope.launch(Dispatchers.IO) {
        delay(SOURCE_UPDATE_DEBOUNCE_MS.milliseconds)
        enqueueSourceRefresh(provider)
      }
      sourceRefreshJobs[provider] = job
      job.invokeOnCompletion {
        synchronized(sourceRefreshJobsLock) {
          if (sourceRefreshJobs[provider] === job) {
            sourceRefreshJobs.remove(provider)
          }
        }
      }
    }
  }

  private fun enqueueSourceRefresh(provider: AgentSessionProvider, scopedPaths: Set<String>? = null) {
    val normalizedScopedPaths = scopedPaths
      ?.asSequence()
      ?.map(::normalizeAgentWorkbenchPath)
      ?.filter { it.isNotBlank() }
      ?.toCollection(LinkedHashSet())
      ?.takeIf { it.isNotEmpty() }

    var shouldStartProcessor = false
    var queueSize = 0
    synchronized(sourceRefreshJobsLock) {
      enqueueSourceRefreshLocked(provider = provider, scopedPaths = normalizedScopedPaths)
      if (!sourceRefreshProcessorRunning) {
        sourceRefreshProcessorRunning = true
        shouldStartProcessor = true
      }
      queueSize = pendingSourceRefreshProviders.size
    }

    LOG.debug {
      val scopeDescription = normalizedScopedPaths?.let { "scopedPaths=${it.size}" } ?: "scopedPaths=all"
      "Enqueued source refresh for ${provider.value} ($scopeDescription, queueSize=$queueSize, startProcessor=$shouldStartProcessor)"
    }

    if (shouldStartProcessor) {
      serviceScope.launch(Dispatchers.IO) {
        processQueuedSourceRefreshes()
      }
    }
  }

  private fun enqueueSourceRefreshLocked(provider: AgentSessionProvider, scopedPaths: Set<String>?) {
    if (!pendingSourceRefreshProviders.containsKey(provider)) {
      pendingSourceRefreshProviders[provider] = scopedPaths
      return
    }
    val existingScope = pendingSourceRefreshProviders[provider]
    pendingSourceRefreshProviders[provider] = mergeSourceRefreshScopes(existingScope, scopedPaths)
  }

  private fun mergeSourceRefreshScopes(existing: Set<String>?, incoming: Set<String>?): Set<String>? {
    if (existing == null || incoming == null) {
      return null
    }
    if (incoming.isEmpty()) {
      return existing
    }
    if (existing.isEmpty()) {
      return incoming
    }
    val merged = LinkedHashSet<String>(existing.size + incoming.size)
    merged.addAll(existing)
    merged.addAll(incoming)
    return merged
  }

  private suspend fun processQueuedSourceRefreshes() {
    while (true) {
      val dequeued = synchronized(sourceRefreshJobsLock) {
        val nextEntry = pendingSourceRefreshProviders.entries.firstOrNull()
        if (nextEntry == null) {
          sourceRefreshProcessorRunning = false
          null
        }
        else {
          pendingSourceRefreshProviders.remove(nextEntry.key)
          Triple(nextEntry.key, nextEntry.value, pendingSourceRefreshProviders.size)
        }
      } ?: run {
        LOG.debug { "Source refresh processor stopped (queue empty)" }
        return
      }

      val provider = dequeued.first
      val scopedPaths = dequeued.second
      val remainingQueueSize = dequeued.third
      val refreshId = sourceRefreshIdCounter.incrementAndGet()
      LOG.debug {
        val scopeDescription = scopedPaths?.let { "scopedPaths=${it.size}" } ?: "scopedPaths=all"
        "Dequeued source refresh id=$refreshId provider=${provider.value} ($scopeDescription, remainingQueueSize=$remainingQueueSize)"
      }

      val gateActive = try {
        isRefreshGateActive()
      }
      catch (e: Throwable) {
        if (e is CancellationException) throw e
        LOG.warn("Failed to evaluate source refresh gate", e)
        false
      }

      LOG.debug {
        "Source refresh gate evaluated for id=$refreshId provider=${provider.value}: active=$gateActive"
      }

      if (!gateActive) {
        var queueSizeAfterRequeue = 0
        synchronized(sourceRefreshJobsLock) {
          enqueueSourceRefreshLocked(provider = provider, scopedPaths = scopedPaths)
          queueSizeAfterRequeue = pendingSourceRefreshProviders.size
        }
        LOG.debug {
          "Source refresh gate blocked id=$refreshId provider=${provider.value}; requeued (queueSize=$queueSizeAfterRequeue)"
        }
        delay(SOURCE_REFRESH_GATE_RETRY_MS.milliseconds)
        continue
      }

      try {
        refreshLoadedProviderThreads(provider = provider, refreshId = refreshId, scopedPaths = scopedPaths)
      }
      catch (e: Throwable) {
        if (e is CancellationException) throw e
        LOG.warn("Failed to refresh ${provider.value} sessions from queued update", e)
      }
    }
  }

  private suspend fun refreshLoadedProviderThreads(provider: AgentSessionProvider, refreshId: Long, scopedPaths: Set<String>?) {
    refreshMutex.withLock {
      LOG.debug { "Starting provider refresh id=$refreshId provider=${provider.value}" }
      val source = sessionSourcesProvider().firstOrNull { it.provider == provider } ?: return
      val stateSnapshot = stateStore.snapshot()
      val knownThreadIdsByPath = collectLoadedProviderThreadIdsByPath(stateSnapshot, provider)
      val targetPaths = LinkedHashSet<String>()
      if (scopedPaths == null) {
        targetPaths.addAll(collectLoadedPaths(stateSnapshot))
        targetPaths.addAll(openAgentChatProjectPathsProvider())
      }
      else {
        targetPaths.addAll(scopedPaths)
      }

      if (targetPaths.isEmpty()) {
        LOG.debug { "Provider refresh id=$refreshId provider=${provider.value} skipped (no target paths)" }
        return
      }

      LOG.debug {
        "Provider refresh id=$refreshId provider=${provider.value} targetPaths=${targetPaths.size}"
      }

      val prefetched = try {
        source.prefetchThreads(targetPaths.toList())
      }
      catch (_: Throwable) {
        emptyMap()
      }

      LOG.debug {
        "Provider refresh id=$refreshId provider=${provider.value} prefetchedPaths=${prefetched.size}"
      }

      val outcomes = LinkedHashMap<String, ProviderRefreshOutcome>(targetPaths.size)
      for (path in targetPaths) {
        val prefetchedThreads = prefetched[path]
        if (prefetchedThreads != null) {
          outcomes[path] = ProviderRefreshOutcome(
            threads = applyArchiveSuppressions(path = path, provider = provider, threads = prefetchedThreads),
          )
          continue
        }

        try {
          outcomes[path] = ProviderRefreshOutcome(
            threads = applyArchiveSuppressions(
              path = path,
              provider = provider,
              threads = source.listThreadsFromClosedProject(path),
            ),
          )
        }
        catch (e: Throwable) {
          if (e is CancellationException) throw e
          LOG.warn("Failed to refresh ${provider.value} sessions for $path", e)
          outcomes[path] = ProviderRefreshOutcome(
            warningMessage = resolveProviderWarningMessage(provider, e),
          )
        }
      }

      val allowedNewThreadIdsByPath = if (provider == AgentSessionProvider.CODEX) {
        calculateNewProviderThreadIdsByPath(
          provider = provider,
          outcomes = outcomes,
          knownThreadIdsByPath = knownThreadIdsByPath,
        )
      }
      else {
        null
      }

      bindPendingOpenChatTabs(
        provider = provider,
        outcomes = outcomes,
        refreshId = refreshId,
        allowedThreadIdsByPath = allowedNewThreadIdsByPath,
      )

      syncOpenChatTabPresentation(provider = provider, outcomes = outcomes, refreshId = refreshId)

      val pendingProjectionPaths = if (provider == AgentSessionProvider.CODEX) {
        mergePendingCodexThreadsFromOpenTabs(
          outcomes = outcomes,
          targetPaths = targetPaths,
          refreshId = refreshId,
        )
      }
      else {
        emptySet()
      }

      stateStore.update { state ->
        var changed = false
        val nextProjects = state.projects.map { project ->
          val shouldApplyProjectOutcome = project.hasLoaded || project.path in pendingProjectionPaths
          val updatedProject = if (shouldApplyProjectOutcome) {
            val outcome = outcomes[project.path]
            if (outcome != null) {
              changed = true
              project.withProviderRefreshOutcome(provider, outcome)
            }
            else {
              project
            }
          }
          else {
            project
          }

          val nextWorktrees = updatedProject.worktrees.map { worktree ->
            val shouldApplyWorktreeOutcome = worktree.hasLoaded || worktree.path in pendingProjectionPaths
            if (!shouldApplyWorktreeOutcome) return@map worktree
            val outcome = outcomes[worktree.path] ?: return@map worktree
            changed = true
            worktree.withProviderRefreshOutcome(provider, outcome)
          }

          if (nextWorktrees == updatedProject.worktrees) {
            updatedProject
          }
          else {
            updatedProject.copy(worktrees = nextWorktrees)
          }
        }

        if (!changed) {
          LOG.debug {
            "Provider refresh id=$refreshId provider=${provider.value} finished without state changes (outcomes=${outcomes.size})"
          }
          state
        }
        else {
          LOG.debug {
            "Provider refresh id=$refreshId provider=${provider.value} applied state changes (outcomes=${outcomes.size})"
          }
          state.copy(
            projects = nextProjects,
            lastUpdatedAt = System.currentTimeMillis(),
          )
        }
      }
      LOG.debug { "Finished provider refresh id=$refreshId provider=${provider.value}" }
    }
  }

  private suspend fun bindPendingOpenChatTabs(
    provider: AgentSessionProvider,
    outcomes: Map<String, ProviderRefreshOutcome>,
    refreshId: Long,
    allowedThreadIdsByPath: Map<String, Set<String>>? = null,
  ) {
    if (provider != AgentSessionProvider.CODEX) {
      return
    }

    val candidatesByPath = LinkedHashMap<String, MutableList<AgentChatPendingTabRebindTarget>>()
    for ((path, outcome) in outcomes) {
      val threads = outcome.threads ?: continue
      val allowedThreadIds = allowedThreadIdsByPath?.get(path)
      if (allowedThreadIdsByPath != null && allowedThreadIds == null) {
        continue
      }
      for (thread in threads) {
        if (thread.provider != provider) continue
        if (allowedThreadIds != null && thread.id !in allowedThreadIds) continue
        val command = runCatching {
          buildAgentSessionResumeCommand(thread.provider, thread.id)
        }.getOrDefault(listOf(provider.value, "resume", thread.id))
        candidatesByPath.getOrPut(path) { ArrayList() }.add(
          AgentChatPendingTabRebindTarget(
            threadIdentity = buildAgentSessionIdentity(thread.provider, thread.id),
            threadId = thread.id,
            shellCommand = command,
            threadTitle = thread.title,
            threadActivity = thread.activity,
            threadUpdatedAt = thread.updatedAt,
          )
        )
      }
    }

    if (candidatesByPath.isEmpty()) {
      return
    }

    val pendingTabsByPath = openPendingCodexTabsProvider()
    if (pendingTabsByPath.isEmpty()) {
      clearPendingCodexAmbiguityState()
      return
    }

    val openConcreteThreadIdentitiesByPath = openConcreteChatThreadIdentitiesByPathProvider()
    val matchResult = CodexPendingTabMatcher.match(
      pendingTabsByPath = pendingTabsByPath,
      candidatesByPath = candidatesByPath,
      openConcreteIdentitiesByPath = openConcreteThreadIdentitiesByPath,
      preWindowMs = PENDING_CODEX_MATCH_PRE_WINDOW_MS,
      postWindowMs = PENDING_CODEX_MATCH_POST_WINDOW_MS,
    )

    reportPendingCodexMatchingGaps(
      refreshId = refreshId,
      ambiguousByPath = matchResult.ambiguousPendingThreadIdentitiesByPath,
      noMatchByPath = matchResult.noMatchPendingThreadIdentitiesByPath,
    )

    val bindingsByPath = matchResult.bindingsByPath
    if (bindingsByPath.isEmpty()) {
      return
    }

    val reboundTabs = withContext(Dispatchers.UI) {
      var rebound = 0
      for ((path, bindings) in bindingsByPath) {
        for (binding in bindings) {
          if (
            openAgentChatPendingTabSpecificBinder(
              path,
              binding.pendingThreadIdentity,
              binding.target,
            )
          ) {
            rebound++
          }
        }
      }
      rebound
    }

    LOG.debug {
      "Provider refresh id=$refreshId provider=${provider.value} rebound pending chat tabs (reboundTabs=$reboundTabs," +
      " candidatePaths=${candidatesByPath.size}, matchedPaths=${bindingsByPath.size})"
    }
  }

  private suspend fun syncOpenChatTabPresentation(
    provider: AgentSessionProvider,
    outcomes: Map<String, ProviderRefreshOutcome>,
    refreshId: Long,
  ) {
    val titleByPathAndThreadIdentity = LinkedHashMap<Pair<String, String>, String>()
    val activityByPathAndThreadIdentity = LinkedHashMap<Pair<String, String>, AgentThreadActivity>()
    for ((path, outcome) in outcomes) {
      val threads = outcome.threads ?: continue
      for (thread in threads) {
        if (thread.provider != provider) continue
        val identityKey = path to buildAgentSessionIdentity(thread.provider, thread.id)
        titleByPathAndThreadIdentity[identityKey] = thread.title
        activityByPathAndThreadIdentity[identityKey] = thread.activity
      }
    }

    if (titleByPathAndThreadIdentity.isEmpty() && activityByPathAndThreadIdentity.isEmpty()) {
      return
    }

    val updatedTabs = openAgentChatTabPresentationUpdater(
      titleByPathAndThreadIdentity,
      activityByPathAndThreadIdentity,
    )

    LOG.debug {
      "Provider refresh id=$refreshId provider=${provider.value} synchronized open chat tab presentation (updatedTabs=$updatedTabs)"
    }
  }

  private fun calculateNewProviderThreadIdsByPath(
    provider: AgentSessionProvider,
    outcomes: Map<String, ProviderRefreshOutcome>,
    knownThreadIdsByPath: Map<String, Set<String>>,
  ): Map<String, Set<String>> {
    val result = LinkedHashMap<String, Set<String>>()
    for ((path, outcome) in outcomes) {
      if (!knownThreadIdsByPath.containsKey(path)) {
        continue
      }
      val knownThreadIds = knownThreadIdsByPath[path].orEmpty()
      val newThreadIds = outcome.threads
        .orEmpty()
        .asSequence()
        .filter { thread -> thread.provider == provider && thread.id !in knownThreadIds }
        .map { thread -> thread.id }
        .toCollection(LinkedHashSet())
      result[path] = newThreadIds
    }
    return result
  }

  private suspend fun mergePendingCodexThreadsFromOpenTabs(
    outcomes: MutableMap<String, ProviderRefreshOutcome>,
    targetPaths: Set<String>,
    refreshId: Long,
  ): Set<String> {
    val pendingTabsByPath = try {
      openPendingCodexTabsProvider()
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      LOG.warn("Failed to collect pending Codex tabs for provider refresh projection", e)
      return emptySet()
    }

    if (pendingTabsByPath.isEmpty() || outcomes.isEmpty()) {
      return emptySet()
    }

    val normalizedTargetPaths = targetPaths
      .asSequence()
      .map(::normalizeAgentWorkbenchPath)
      .toHashSet()
    val outcomePathByNormalizedPath = LinkedHashMap<String, String>()
    outcomes.keys.forEach { path ->
      outcomePathByNormalizedPath.putIfAbsent(normalizeAgentWorkbenchPath(path), path)
    }

    val projectedPaths = LinkedHashSet<String>()
    var projectedThreads = 0
    for ((path, pendingTabs) in pendingTabsByPath) {
      val normalizedPath = normalizeAgentWorkbenchPath(path)
      if (normalizedPath !in normalizedTargetPaths) {
        continue
      }

      val outcomePath = outcomePathByNormalizedPath[normalizedPath] ?: continue
      val pendingThreads = buildPendingCodexThreads(pendingTabs)
      if (pendingThreads.isEmpty()) {
        continue
      }

      val existingOutcome = outcomes[outcomePath] ?: ProviderRefreshOutcome()
      val mergedThreads = mergeProviderThreadsWithPendingCodex(
        sourceThreads = existingOutcome.threads.orEmpty(),
        pendingThreads = pendingThreads,
      )
      outcomes[outcomePath] = existingOutcome.copy(threads = mergedThreads)
      projectedPaths += normalizedPath
      projectedThreads += pendingThreads.size
    }

    if (projectedPaths.isNotEmpty()) {
      LOG.debug {
        "Provider refresh id=$refreshId provider=codex projected pending rows " +
        "(paths=${projectedPaths.size}, threads=$projectedThreads)"
      }
    }

    return projectedPaths
  }

  private fun buildPendingCodexThreads(
    pendingTabs: List<AgentChatPendingCodexTabSnapshot>,
  ): List<AgentSessionThread> {
    val threadsById = LinkedHashMap<String, AgentSessionThread>()
    for (pendingTab in pendingTabs) {
      val identity = parseAgentSessionIdentity(pendingTab.pendingThreadIdentity) ?: continue
      if (identity.provider != AgentSessionProvider.CODEX) continue
      if (!isAgentSessionNewSessionId(identity.sessionId)) continue

      val updatedAt = pendingTab.pendingFirstInputAtMs ?: pendingTab.pendingCreatedAtMs ?: 0L
      val pendingThread = AgentSessionThread(
        id = identity.sessionId,
        title = AgentSessionsBundle.message("toolwindow.action.new.thread"),
        updatedAt = updatedAt,
        archived = false,
        activity = AgentThreadActivity.READY,
        provider = AgentSessionProvider.CODEX,
      )
      val existing = threadsById[identity.sessionId]
      if (existing == null || pendingThread.updatedAt > existing.updatedAt) {
        threadsById[identity.sessionId] = pendingThread
      }
    }
    return threadsById.values.toList()
  }

  private fun mergeProviderThreadsWithPendingCodex(
    sourceThreads: List<AgentSessionThread>,
    pendingThreads: List<AgentSessionThread>,
  ): List<AgentSessionThread> {
    if (pendingThreads.isEmpty()) {
      return sourceThreads
    }

    val threadsById = LinkedHashMap<String, AgentSessionThread>(sourceThreads.size + pendingThreads.size)
    sourceThreads.forEach { thread ->
      threadsById[thread.id] = thread
    }
    pendingThreads.forEach { pendingThread ->
      val existing = threadsById[pendingThread.id]
      threadsById[pendingThread.id] = if (existing == null || pendingThread.updatedAt >= existing.updatedAt) {
        pendingThread
      }
      else {
        existing
      }
    }
    return threadsById.values.sortedByDescending { thread -> thread.updatedAt }
  }

  private fun clearPendingCodexAmbiguityState() {
    synchronized(pendingCodexAmbiguityLock) {
      pendingCodexAmbiguityStateByKey.clear()
    }
  }

  private fun reportPendingCodexMatchingGaps(
    refreshId: Long,
    ambiguousByPath: Map<String, Set<String>>,
    noMatchByPath: Map<String, Set<String>>,
  ) {
    val trackedKeys = LinkedHashSet<String>()
    val now = System.currentTimeMillis()

    for ((path, pendingIdentities) in ambiguousByPath) {
      for (pendingIdentity in pendingIdentities) {
        val key = "$path|$pendingIdentity"
        trackedKeys.add(key)

        var shouldWarn = false
        synchronized(pendingCodexAmbiguityLock) {
          val previous = pendingCodexAmbiguityStateByKey[key]
          val nextPollCount = (previous?.pollCount ?: 0) + 1
          val lastWarnedAtMs = previous?.lastWarnedAtMs
          if (
            nextPollCount >= PENDING_CODEX_AMBIGUITY_NOTIFY_AFTER_POLLS &&
            (lastWarnedAtMs == null || now - lastWarnedAtMs >= PENDING_CODEX_AMBIGUITY_NOTIFY_COOLDOWN_MS)
          ) {
            shouldWarn = true
            pendingCodexAmbiguityStateByKey[key] = PendingCodexAmbiguityState(
              pollCount = nextPollCount,
              lastWarnedAtMs = now,
            )
          }
          else {
            pendingCodexAmbiguityStateByKey[key] = PendingCodexAmbiguityState(
              pollCount = nextPollCount,
              lastWarnedAtMs = lastWarnedAtMs,
            )
          }
        }

        if (shouldWarn) {
          LOG.warn(
            "Provider refresh id=$refreshId provider=codex skipped ambiguous pending tab binding for path=$path, " +
            "pendingIdentity=$pendingIdentity. Use editor tab action 'Bind Pending Codex Thread'."
          )
        }
      }
    }

    for ((path, pendingIdentities) in noMatchByPath) {
      for (pendingIdentity in pendingIdentities) {
        trackedKeys.add("$path|$pendingIdentity")
      }
    }

    synchronized(pendingCodexAmbiguityLock) {
      pendingCodexAmbiguityStateByKey.keys.retainAll(trackedKeys)
    }
  }

  private suspend fun markOnDemandLoading(path: String): Boolean {
    return onDemandMutex.withLock {
      val project = stateStore.state.value.projects.firstOrNull { it.path == path } ?: return@withLock false
      if (project.isOpen || project.isLoading || project.hasLoaded) return@withLock false
      if (!onDemandLoading.add(path)) return@withLock false
      true
    }
  }

  private suspend fun clearOnDemandLoading(path: String) {
    onDemandMutex.withLock {
      onDemandLoading.remove(path)
    }
  }

  private suspend fun markWorktreeOnDemandLoading(projectPath: String, worktreePath: String): Boolean {
    return onDemandMutex.withLock {
      val project = stateStore.state.value.projects.firstOrNull { it.path == projectPath } ?: return@withLock false
      val worktree = project.worktrees.firstOrNull { it.path == worktreePath } ?: return@withLock false
      if (worktree.isLoading || worktree.hasLoaded) return@withLock false
      if (!onDemandWorktreeLoading.add(worktreePath)) return@withLock false
      true
    }
  }

  private suspend fun clearWorktreeOnDemandLoading(worktreePath: String) {
    onDemandMutex.withLock {
      onDemandWorktreeLoading.remove(worktreePath)
    }
  }

  private suspend fun loadThreadsFromClosedProject(path: String): AgentSessionLoadResult {
    return loadThreads(path) { source ->
      source.listThreadsFromClosedProject(path = path)
    }
  }

  private suspend fun loadThreads(
    path: String,
    loadOperation: suspend (AgentSessionSource) -> List<AgentSessionThread>,
  ): AgentSessionLoadResult {
    val sessionSources = sessionSourcesProvider()
    val sourceResults = coroutineScope {
      sessionSources.map { source ->
        async {
          val result = try {
            Result.success(
              applyArchiveSuppressions(
                path = path,
                provider = source.provider,
                threads = loadOperation(source),
              ),
            )
          }
          catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            LOG.warn("Failed to load ${source.provider.value} sessions for $path", throwable)
            Result.failure(throwable)
          }
          AgentSessionSourceLoadResult(
            provider = source.provider,
            result = result,
            hasUnknownTotal = result.isSuccess && !source.canReportExactThreadCount,
          )
        }
      }.awaitAll()
    }
    return mergeAgentSessionSourceLoadResults(
      sourceResults = sourceResults,
      resolveErrorMessage = ::resolveErrorMessage,
      resolveWarningMessage = ::resolveProviderWarningMessage,
    )
  }

  private suspend fun loadSourceResultForOpenProject(
    source: AgentSessionSource,
    normalizedPath: String,
    project: Project,
    prefetchedByProvider: Map<AgentSessionProvider, Map<String, List<AgentSessionThread>>>,
    originalPath: String,
  ): AgentSessionSourceLoadResult {
    return try {
      val prefetched = prefetchedByProvider.get(source.provider)?.get(normalizedPath)
      val threads = applyArchiveSuppressions(
        path = normalizedPath,
        provider = source.provider,
        threads = prefetched ?: source.listThreadsFromOpenProject(path = normalizedPath, project = project),
      )
      AgentSessionSourceLoadResult(
        provider = source.provider,
        result = Result.success(threads),
        hasUnknownTotal = !source.canReportExactThreadCount,
      )
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      LOG.warn("Failed to load ${source.provider.value} sessions for $originalPath", e)
      AgentSessionSourceLoadResult(
        provider = source.provider,
        result = Result.failure(e),
      )
    }
  }

  private suspend fun loadSourcesIncrementally(
    sessionSources: List<AgentSessionSource>,
    normalizedPath: String,
    project: Project,
    prefetchedByProvider: Map<AgentSessionProvider, Map<String, List<AgentSessionThread>>>,
    originalPath: String,
    onPartialResult: (AgentSessionLoadResult, isComplete: Boolean) -> Unit,
  ): AgentSessionLoadResult {
    val sourceResults = java.util.concurrent.CopyOnWriteArrayList<AgentSessionSourceLoadResult>()
    val totalSourceCount = sessionSources.size
    coroutineScope {
      for (source in sessionSources) {
        launch {
          val sourceResult = loadSourceResultForOpenProject(
            source = source,
            normalizedPath = normalizedPath,
            project = project,
            prefetchedByProvider = prefetchedByProvider,
            originalPath = originalPath,
          )
          sourceResults.add(sourceResult)
          val partial = mergeAgentSessionSourceLoadResults(
            sourceResults = sourceResults.toList(),
            resolveErrorMessage = ::resolveErrorMessage,
            resolveWarningMessage = ::resolveProviderWarningMessage,
          )
          onPartialResult(partial, sourceResults.size == totalSourceCount)
        }
      }
    }
    return mergeAgentSessionSourceLoadResults(
      sourceResults = sourceResults.toList(),
      resolveErrorMessage = ::resolveErrorMessage,
      resolveWarningMessage = ::resolveProviderWarningMessage,
    )
  }

  private fun applyArchiveSuppressions(
    path: String,
    provider: AgentSessionProvider,
    threads: List<AgentSessionThread>,
  ): List<AgentSessionThread> {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val suppressedThreadIds = synchronized(archiveSuppressionsLock) {
      archiveSuppressions.asSequence()
        .filter { suppression -> suppression.path == normalizedPath && suppression.provider == provider }
        .map { suppression -> suppression.threadId }
        .toHashSet()
    }
    if (suppressedThreadIds.isEmpty()) {
      return threads
    }
    return threads.filterNot { thread -> thread.id in suppressedThreadIds }
  }
}

private fun collectLoadedPaths(state: AgentSessionsState): List<String> {
  val paths = LinkedHashSet<String>()
  for (project in state.projects) {
    if (project.hasLoaded) {
      paths.add(project.path)
    }
    for (worktree in project.worktrees) {
      if (worktree.hasLoaded) {
        paths.add(worktree.path)
      }
    }
  }
  return ArrayList(paths)
}

private fun collectLoadedProviderThreadIdsByPath(
  state: AgentSessionsState,
  provider: AgentSessionProvider,
): Map<String, Set<String>> {
  val result = LinkedHashMap<String, Set<String>>()
  for (project in state.projects) {
    if (project.hasLoaded) {
      result[project.path] = project.threads
        .asSequence()
        .filter { it.provider == provider }
        .map { it.id }
        .toCollection(LinkedHashSet())
    }
    for (worktree in project.worktrees) {
      if (!worktree.hasLoaded) {
        continue
      }
      result[worktree.path] = worktree.threads
        .asSequence()
        .filter { it.provider == provider }
        .map { it.id }
        .toCollection(LinkedHashSet())
    }
  }
  return result
}

private fun resolveErrorMessage(provider: AgentSessionProvider, t: Throwable): String {
  return if (isCliMissingError(provider, t)) resolveCliMissingMessage(provider)
  else AgentSessionsBundle.message("toolwindow.error")
}

private fun resolveCliMissingMessage(provider: AgentSessionProvider): String {
  return if (AgentSessionProviderBridges.find(provider) != null) {
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
  return AgentSessionProviderBridges.find(provider)?.isCliMissingError(t) == true
}

private fun resolveProviderLabel(provider: AgentSessionProvider): String {
  val bridge = AgentSessionProviderBridges.find(provider)
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

private fun AgentProjectSessions.withProviderRefreshOutcome(
  provider: AgentSessionProvider,
  outcome: ProviderRefreshOutcome,
): AgentProjectSessions {
  val mergedThreads = outcome.threads?.let { threads ->
    mergeThreadsForProvider(this.threads, provider, threads)
  } ?: this.threads
  return copy(
    threads = mergedThreads,
    providerWarnings = replaceProviderWarning(this.providerWarnings, provider, outcome.warningMessage),
  )
}

private fun AgentWorktree.withProviderRefreshOutcome(
  provider: AgentSessionProvider,
  outcome: ProviderRefreshOutcome,
): AgentWorktree {
  val mergedThreads = outcome.threads?.let { threads ->
    mergeThreadsForProvider(this.threads, provider, threads)
  } ?: this.threads
  return copy(
    threads = mergedThreads,
    providerWarnings = replaceProviderWarning(this.providerWarnings, provider, outcome.warningMessage),
  )
}

private fun replaceProviderWarning(
  warnings: List<AgentSessionProviderWarning>,
  provider: AgentSessionProvider,
  warningMessage: String?,
): List<AgentSessionProviderWarning> {
  val withoutProvider = warnings.filterNot { it.provider == provider }
  return if (warningMessage == null) {
    withoutProvider
  }
  else {
    withoutProvider + AgentSessionProviderWarning(provider = provider, message = warningMessage)
  }
}

private fun mergeThreadsForProvider(
  existingThreads: List<AgentSessionThread>,
  provider: AgentSessionProvider,
  newProviderThreads: List<AgentSessionThread>,
): List<AgentSessionThread> {
  val mergedThreads = ArrayList<AgentSessionThread>(existingThreads.size + newProviderThreads.size)
  existingThreads.filterTo(mergedThreads) { it.provider != provider }
  mergedThreads.addAll(newProviderThreads)
  mergedThreads.sortByDescending { it.updatedAt }
  return mergedThreads
}

private fun List<AgentSessionThreadPreview>.toCachedSessionThreads(): List<AgentSessionThread> {
  return map { preview ->
    AgentSessionThread(
      id = preview.id,
      title = preview.title,
      updatedAt = preview.updatedAt,
      archived = false,
      provider = preview.provider,
    )
  }
}

private fun List<AgentSessionThread>.toThreadPreviews(): List<AgentSessionThreadPreview> {
  return map { thread ->
    AgentSessionThreadPreview(
      id = thread.id,
      title = thread.title,
      updatedAt = thread.updatedAt,
      provider = thread.provider,
    )
  }
}

private data class ProviderRefreshOutcome(
  @JvmField val threads: List<AgentSessionThread>? = null,
  @JvmField val warningMessage: String? = null,
)

private data class PendingCodexAmbiguityState(
  @JvmField val pollCount: Int,
  @JvmField val lastWarnedAtMs: Long?,
)

private data class ArchiveSuppression(
  @JvmField val path: String,
  val provider: AgentSessionProvider,
  @JvmField val threadId: String,
)
