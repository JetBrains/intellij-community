// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatConcreteCodexTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatConcreteCodexTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatConcreteCodexTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabSnapshot
import com.intellij.agent.workbench.chat.agentChatScopedRefreshSignals
import com.intellij.agent.workbench.chat.clearOpenConcreteAgentChatNewThreadRebindAnchors
import com.intellij.agent.workbench.chat.collectOpenAgentChatProjectPaths
import com.intellij.agent.workbench.chat.collectOpenConcreteAgentChatTabsAwaitingNewThreadRebindByPath
import com.intellij.agent.workbench.chat.collectOpenConcreteAgentChatThreadIdentitiesByPath
import com.intellij.agent.workbench.chat.collectOpenPendingAgentChatTabsByPath
import com.intellij.agent.workbench.chat.collectSelectedChatThreadIdentity
import com.intellij.agent.workbench.chat.rebindOpenConcreteAgentChatTabs
import com.intellij.agent.workbench.chat.rebindOpenPendingAgentChatTabs
import com.intellij.agent.workbench.chat.updateOpenAgentChatTabPresentation
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBehaviors
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderWarning
import com.intellij.agent.workbench.sessions.model.AgentSessionThreadPreview
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.model.ProjectEntry
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.state.SessionsTreeUiState
import com.intellij.agent.workbench.sessions.util.agentSessionCliMissingMessageKey
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<AgentSessionRefreshCoordinator>()
private const val SOURCE_UPDATE_DEBOUNCE_MS = 350L
private const val SOURCE_REFRESH_GATE_RETRY_MS = 500L

internal class AgentSessionRefreshCoordinator(
    private val serviceScope: CoroutineScope,
    private val sessionSourcesProvider: () -> List<AgentSessionSource>,
    private val projectEntriesProvider: suspend () -> List<ProjectEntry>,
    private val stateStore: AgentSessionsStateStore,
    private val contentRepository: AgentSessionContentRepository,
    private val isRefreshGateActive: suspend () -> Boolean,
    private val openAgentChatProjectPathsProvider: suspend () -> Set<String> = ::collectOpenAgentChatProjectPaths,
    private val codexScopedRefreshSignalsProvider: (AgentSessionProvider) -> Flow<Set<String>> = { provider ->
      agentChatScopedRefreshSignals(provider)
    },
    private val openPendingCodexTabsProvider: suspend (AgentSessionProvider) -> Map<String, List<AgentChatPendingCodexTabSnapshot>> =
    ::collectOpenPendingAgentChatTabsByPath,
    private val openConcreteCodexTabsAwaitingNewThreadRebindProvider: suspend (AgentSessionProvider) -> Map<String, List<AgentChatConcreteCodexTabSnapshot>> =
    ::collectOpenConcreteAgentChatTabsAwaitingNewThreadRebindByPath,
    private val openConcreteChatThreadIdentitiesByPathProvider: suspend () -> Map<String, Set<String>> = ::collectOpenConcreteAgentChatThreadIdentitiesByPath,
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
  private val selectedChatThreadIdentityProvider: suspend () -> Pair<AgentSessionProvider, String>? = ::collectSelectedChatThreadIdentity,
) {
  private val refreshMutex = Mutex()
  private val refreshQueueLock = Any()
  private var pendingRefreshRequest: RefreshRequestType? = null
  private var refreshProcessorRunning = false
  private val sourceRefreshJobs = LinkedHashMap<AgentSessionProvider, PendingSourceRefreshJob>()
  private val sourceRefreshJobsLock = Any()
  private val pendingSourceRefreshProviders = LinkedHashMap<AgentSessionProvider, QueuedSourceRefreshRequest>()
  private var sourceRefreshProcessorRunning = false
  private val sourceRefreshIdCounter = AtomicLong()
  private val sourceObserverJobs = LinkedHashMap<AgentSessionProvider, Job>()
  private val sourceObserverJobsLock = Any()
  private val scopedRefreshObserverJobs = LinkedHashMap<AgentSessionProvider, Job>()
  private val scopedRefreshObserverJobsLock = Any()
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

  fun observeSessionSourceUpdates() {
    ensureSourceUpdateObservers()
    ensureScopedRefreshObservers()
  }

  private fun ensureScopedRefreshObservers() {
    val providers = AgentSessionProviderBehaviors.allBehaviors()
      .asSequence()
      .filter { behavior -> behavior.emitsScopedRefreshSignals }
      .map { behavior -> behavior.provider }
      .toList()
    synchronized(scopedRefreshObserverJobsLock) {
      providers.forEach { provider ->
        val existing = scopedRefreshObserverJobs[provider]
        if (existing != null && existing.isActive) {
          return@forEach
        }
        scopedRefreshObserverJobs[provider] = serviceScope.launch(Dispatchers.IO) {
          try {
            codexScopedRefreshSignalsProvider(provider).collect { scopedPaths ->
              if (scopedPaths.isEmpty()) {
                return@collect
              }
              LOG.debug {
                "Received scoped refresh signal for ${provider.value} (paths=${scopedPaths.size}); scheduling scoped provider refresh"
              }
              enqueueSourceRefresh(
                provider = provider,
                scopedPaths = scopedPaths,
                sourceUpdate = AgentSessionSourceUpdate.THREADS_CHANGED,
              )
            }
          }
          catch (e: Throwable) {
            if (e is CancellationException) throw e
            LOG.warn("Scoped refresh observer failed for ${provider.value}", e)
          }
        }
      }
    }
  }

  private fun refreshSupportFor(provider: AgentSessionProvider): AgentSessionCodexRefreshSupport? {
    val behavior = AgentSessionProviderBehaviors.find(provider) ?: return null
    if (!behavior.supportsPendingEditorTabRebind && !behavior.supportsNewThreadRebind) {
      return null
    }
    return synchronized(providerRefreshSupportLock) {
      providerRefreshSupportByProvider.getOrPut(provider) {
        AgentSessionCodexRefreshSupport(
          provider = provider,
          openPendingCodexTabsProvider = openPendingCodexTabsProvider,
          openConcreteCodexTabsAwaitingNewThreadRebindProvider = openConcreteCodexTabsAwaitingNewThreadRebindProvider,
          openConcreteChatThreadIdentitiesByPathProvider = openConcreteChatThreadIdentitiesByPathProvider,
          openAgentChatPendingTabsBinder = openAgentChatPendingTabsBinder,
          openAgentChatConcreteTabsBinder = openAgentChatConcreteTabsBinder,
          clearOpenConcreteCodexTabAnchors = clearOpenConcreteCodexTabAnchors,
        )
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
            source.updateEvents.collect { sourceUpdate ->
              scheduleSourceRefresh(provider, sourceUpdate)
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
    archiveSuppressionSupport.suppress(path = path, provider = provider, threadId = threadId)
  }

  fun unsuppressArchivedThread(path: String, provider: AgentSessionProvider, threadId: String) {
    archiveSuppressionSupport.unsuppress(path = path, provider = provider, threadId = threadId)
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

  private fun scheduleSourceRefresh(provider: AgentSessionProvider, sourceUpdate: AgentSessionSourceUpdate) {
    synchronized(sourceRefreshJobsLock) {
      val existingJob = sourceRefreshJobs.remove(provider)
      existingJob?.job?.cancel()
      val mergedUpdate = mergeSourceUpdateTypes(existingJob?.sourceUpdate, sourceUpdate)
      LOG.debug {
        "Scheduled debounced source refresh for ${provider.value} (sourceUpdate=${mergedUpdate.name.lowercase()})"
      }
      val job = serviceScope.launch(Dispatchers.IO) {
        delay(SOURCE_UPDATE_DEBOUNCE_MS.milliseconds)
        enqueueSourceRefresh(provider = provider, sourceUpdate = mergedUpdate)
      }
      sourceRefreshJobs[provider] = PendingSourceRefreshJob(job = job, sourceUpdate = mergedUpdate)
      job.invokeOnCompletion {
        synchronized(sourceRefreshJobsLock) {
          if (sourceRefreshJobs[provider]?.job === job) {
            sourceRefreshJobs.remove(provider)
          }
        }
      }
    }
  }

  private fun enqueueSourceRefresh(
    provider: AgentSessionProvider,
    scopedPaths: Set<String>? = null,
    sourceUpdate: AgentSessionSourceUpdate = AgentSessionSourceUpdate.THREADS_CHANGED,
  ) {
    val normalizedScopedPaths = scopedPaths
      ?.asSequence()
      ?.map(::normalizeAgentWorkbenchPath)
      ?.filter { it.isNotBlank() }
      ?.toCollection(LinkedHashSet())
      ?.takeIf { it.isNotEmpty() }

    var shouldStartProcessor = false
    var queueSize = 0
    synchronized(sourceRefreshJobsLock) {
      enqueueSourceRefreshLocked(
        provider = provider,
        scopedPaths = normalizedScopedPaths,
        sourceUpdate = sourceUpdate,
      )
      if (!sourceRefreshProcessorRunning) {
        sourceRefreshProcessorRunning = true
        shouldStartProcessor = true
      }
      queueSize = pendingSourceRefreshProviders.size
    }

    LOG.debug {
      val scopeDescription = normalizedScopedPaths?.let { "scopedPaths=${it.size}" } ?: "scopedPaths=all"
      "Enqueued source refresh for ${provider.value} " +
      "($scopeDescription, sourceUpdate=${sourceUpdate.name.lowercase()}, queueSize=$queueSize, startProcessor=$shouldStartProcessor)"
    }

    if (shouldStartProcessor) {
      serviceScope.launch(Dispatchers.IO) {
        processQueuedSourceRefreshes()
      }
    }
  }

  private fun enqueueSourceRefreshLocked(
    provider: AgentSessionProvider,
    scopedPaths: Set<String>?,
    sourceUpdate: AgentSessionSourceUpdate,
  ) {
    val existingRequest = pendingSourceRefreshProviders[provider]
    if (existingRequest == null) {
      pendingSourceRefreshProviders[provider] = QueuedSourceRefreshRequest(
        scopedPaths = scopedPaths,
        sourceUpdate = sourceUpdate,
      )
      return
    }
    pendingSourceRefreshProviders[provider] = QueuedSourceRefreshRequest(
      scopedPaths = mergeSourceRefreshScopes(existingRequest.scopedPaths, scopedPaths),
      sourceUpdate = mergeSourceUpdateTypes(existingRequest.sourceUpdate, sourceUpdate),
    )
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

  private fun mergeSourceUpdateTypes(
    existing: AgentSessionSourceUpdate?,
    incoming: AgentSessionSourceUpdate,
  ): AgentSessionSourceUpdate {
    if (existing == null) {
      return incoming
    }
    return when {
      existing == AgentSessionSourceUpdate.THREADS_CHANGED || incoming == AgentSessionSourceUpdate.THREADS_CHANGED -> AgentSessionSourceUpdate.THREADS_CHANGED
      else -> AgentSessionSourceUpdate.HINTS_CHANGED
    }
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
      val request = dequeued.second
      val scopedPaths = request.scopedPaths
      val sourceUpdate = request.sourceUpdate
      val remainingQueueSize = dequeued.third
      val refreshId = sourceRefreshIdCounter.incrementAndGet()
      LOG.debug {
        val scopeDescription = scopedPaths?.let { "scopedPaths=${it.size}" } ?: "scopedPaths=all"
        "Dequeued source refresh id=$refreshId provider=${provider.value} " +
        "($scopeDescription, sourceUpdate=${sourceUpdate.name.lowercase()}, remainingQueueSize=$remainingQueueSize)"
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
          enqueueSourceRefreshLocked(
            provider = provider,
            scopedPaths = scopedPaths,
            sourceUpdate = sourceUpdate,
          )
          queueSizeAfterRequeue = pendingSourceRefreshProviders.size
        }
        LOG.debug {
          "Source refresh gate blocked id=$refreshId provider=${provider.value}; requeued (queueSize=$queueSizeAfterRequeue)"
        }
        delay(SOURCE_REFRESH_GATE_RETRY_MS.milliseconds)
        continue
      }

      try {
        refreshLoadedProviderThreads(
          provider = provider,
          refreshId = refreshId,
          scopedPaths = scopedPaths,
          sourceUpdate = sourceUpdate,
        )
      }
      catch (e: Throwable) {
        if (e is CancellationException) throw e
        LOG.warn("Failed to refresh ${provider.value} sessions from queued update", e)
      }
    }
  }

  private suspend fun refreshLoadedProviderThreads(
    provider: AgentSessionProvider,
    refreshId: Long,
    scopedPaths: Set<String>?,
    sourceUpdate: AgentSessionSourceUpdate,
  ) {
    refreshMutex.withLock {
      LOG.debug {
        "Starting provider refresh id=$refreshId provider=${provider.value} (sourceUpdate=${sourceUpdate.name.lowercase()})"
      }
      val source = sessionSourcesProvider().firstOrNull { it.provider == provider } ?: return
      val selectedIdentity = selectedChatThreadIdentityProvider()
      source.setActiveThreadId(
        if (selectedIdentity != null && selectedIdentity.first == provider) selectedIdentity.second else null
      )
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
            threads = archiveSuppressionSupport.apply(path = path, provider = provider, threads = prefetchedThreads),
          )
          continue
        }

        try {
          outcomes[path] = ProviderRefreshOutcome(
            threads = archiveSuppressionSupport.apply(
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

      val refreshSupport = refreshSupportFor(provider)
      val pendingCodexTabsSnapshotByPath = refreshSupport?.collectNormalizedPendingTabsByPath() ?: emptyMap()

      val concreteCodexTabsSnapshotByPath = refreshSupport?.collectNormalizedConcreteTabsAwaitingNewThreadRebindByPath() ?: emptyMap()

      val codexHintThreadIdsByPath = refreshSupport?.collectRefreshHintThreadIdsByPath(
          targetPaths = targetPaths,
          outcomes = outcomes,
          knownThreadIdsByPath = knownThreadIdsByPath,
          pendingTabsByPath = pendingCodexTabsSnapshotByPath,
          concreteTabsByPath = concreteCodexTabsSnapshotByPath,
        ) ?: emptyMap()

      val refreshHintPaths = if (refreshSupport != null) {
        targetPaths
          .asSequence()
          .filter { path ->
            codexHintThreadIdsByPath.containsKey(path) || pendingCodexTabsSnapshotByPath[path]?.isNotEmpty() == true
          }
          .toCollection(LinkedHashSet())
      }
      else {
        emptySet()
      }

      val refreshHintsByPath = if (refreshHintPaths.isEmpty()) {
        emptyMap()
      }
      else {
        try {
          source.prefetchRefreshHints(
            paths = refreshHintPaths.toList(),
            knownThreadIdsByPath = codexHintThreadIdsByPath.filterKeys { it in refreshHintPaths },
          )
        }
        catch (e: Throwable) {
          if (e is CancellationException) throw e
          LOG.warn("Failed to fetch ${provider.value} refresh hints", e)
          emptyMap()
        }
      }

      if (refreshSupport != null && refreshHintsByPath.isNotEmpty()) {
        refreshSupport.applyActivityHints(
          outcomes = outcomes,
          refreshHintsByPath = refreshHintsByPath,
        )
      }

      val allowedNewThreadIdsByPath = if (refreshSupport != null) {
        calculateNewProviderThreadIdsByPath(
          provider = provider,
          outcomes = outcomes,
          knownThreadIdsByPath = knownThreadIdsByPath,
        )
      }
      else {
        null
      }

      refreshSupport?.bindConcreteOpenChatTabsAwaitingNewThread(
          refreshId = refreshId,
          refreshHintsByPath = refreshHintsByPath,
          concreteTabsByPath = concreteCodexTabsSnapshotByPath,
        )

      val pendingCodexBindOutcome = refreshSupport?.bindPendingOpenChatTabs(
          outcomes = outcomes,
          refreshId = refreshId,
          allowedThreadIdsByPath = allowedNewThreadIdsByPath,
          refreshHintsByPath = refreshHintsByPath,
          pendingTabsByPath = pendingCodexTabsSnapshotByPath,
        )

      val pendingCodexTabsForProjectionByPath =
        pendingCodexBindOutcome?.pendingTabsForProjectionByPath ?: pendingCodexTabsSnapshotByPath

      syncOpenChatTabPresentation(provider = provider, outcomes = outcomes, refreshId = refreshId)

      val pendingProjectionPaths = refreshSupport?.mergePendingThreadsFromOpenTabs(
          outcomes = outcomes,
          targetPaths = targetPaths,
          refreshId = refreshId,
          pendingTabsByPath = pendingCodexTabsForProjectionByPath,
        ) ?: emptySet()

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
      contentRepository.syncWarmSnapshotsFromRuntime(targetPaths)
      LOG.debug { "Finished provider refresh id=$refreshId provider=${provider.value}" }
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
      activity = preview.activity,
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
      activity = thread.activity,
      provider = thread.provider,
    )
  }
}

private data class PendingSourceRefreshJob(
  @JvmField val job: Job,
  @JvmField val sourceUpdate: AgentSessionSourceUpdate,
)

private data class QueuedSourceRefreshRequest(
  @JvmField val scopedPaths: Set<String>?,
  @JvmField val sourceUpdate: AgentSessionSourceUpdate,
)

internal data class ProviderRefreshOutcome(
  @JvmField val threads: List<AgentSessionThread>? = null,
  @JvmField val warningMessage: String? = null,
)
