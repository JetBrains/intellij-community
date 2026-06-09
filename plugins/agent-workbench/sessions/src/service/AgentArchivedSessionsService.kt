// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.normalizeConcreteAgentSessionThreadId
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.model.AgentArchivedSessionsState
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.model.ProjectEntry
import com.intellij.agent.workbench.sessions.settings.AgentSessionProviderSettingsListener
import com.intellij.agent.workbench.sessions.settings.AgentSessionProviderSettingsService
import com.intellij.agent.workbench.sessions.state.DEFAULT_VISIBLE_CLOSED_PROJECT_COUNT
import com.intellij.agent.workbench.sessions.state.DEFAULT_VISIBLE_THREAD_COUNT
import com.intellij.agent.workbench.sessions.util.agentSessionCliMissingMessageKey
import com.intellij.openapi.components.service
import com.intellij.openapi.components.Service
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private val ARCHIVED_LOG = logger<AgentArchivedSessionsService>()

@Suppress("DuplicatedCode")
@Service(Service.Level.APP)
class AgentArchivedSessionsService internal constructor(
  private val serviceScope: CoroutineScope,
  private val sessionSourcesProvider: () -> List<AgentSessionSource>,
  private val projectEntriesProvider: suspend () -> List<ProjectEntry>,
) {
  @Suppress("unused")
  constructor(serviceScope: CoroutineScope) : this(
    serviceScope = serviceScope,
    sessionSourcesProvider = {
      service<AgentSessionProviderSettingsService>().enabledSessionSources(AgentSessionProviders.sessionSources())
    },
    projectEntriesProvider = AgentSessionProjectCatalog()::collectProjects,
  )

  private val mutableState = MutableStateFlow(AgentArchivedSessionsState())
  private val refreshMutex = Mutex()
  private val loadRequested = AtomicBoolean(false)
  private val costCache = ConcurrentHashMap<ArchivedThreadCacheKey, ArchivedThreadCostCacheEntry>()
  private val inFlightCostLoads = ConcurrentHashMap.newKeySet<ArchivedThreadLoadKey>()

  init {
    ApplicationManager.getApplication().messageBus.connect(serviceScope)
      .subscribe(AgentSessionProviderSettingsListener.TOPIC, object : AgentSessionProviderSettingsListener {
        override fun providerSettingsChanged() {
          refreshIfLoaded()
        }
      })
    serviceScope.launch(Dispatchers.Default) {
      mutableState.collectLatest(::hydrateVisibleThreadCosts)
    }
  }

  fun stateFlow(): StateFlow<AgentArchivedSessionsState> = mutableState.asStateFlow()

  fun snapshot(): AgentArchivedSessionsState = mutableState.value

  fun ensureLoaded() {
    loadRequested.set(true)
    val state = mutableState.value
    if (state.lastUpdatedAt == null && !state.projects.anyPathLoading()) {
      refresh()
    }
  }

  fun refreshIfLoaded() {
    if (loadRequested.get() || mutableState.value.lastUpdatedAt != null) {
      refresh()
    }
  }

  fun refresh() {
    loadRequested.set(true)
    serviceScope.launch(Dispatchers.IO) {
      refreshMutex.withLock {
        refreshNow()
      }
    }
  }

  fun showMoreProjects() {
    mutableState.update { state ->
      state.copy(visibleClosedProjectCount = state.visibleClosedProjectCount + DEFAULT_VISIBLE_CLOSED_PROJECT_COUNT)
    }
  }

  fun showMoreThreads(path: String) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    mutableState.update { state ->
      val current = state.visibleThreadCounts[normalizedPath] ?: DEFAULT_VISIBLE_THREAD_COUNT
      state.copy(visibleThreadCounts = state.visibleThreadCounts + (normalizedPath to current + DEFAULT_VISIBLE_THREAD_COUNT))
    }
  }

  fun ensureProjectVisible(path: String) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    mutableState.update { state ->
      val requiredClosedProjectCount = requiredVisibleClosedProjectCount(state.projects, normalizedPath) ?: return@update state
      if (requiredClosedProjectCount <= state.visibleClosedProjectCount) {
        state
      }
      else {
        state.copy(visibleClosedProjectCount = requiredClosedProjectCount)
      }
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

  private suspend fun refreshNow() {
    val entries = projectEntriesProvider()
    val previous = mutableState.value
    val previousProjectsByPath = previous.projects.associateBy { project -> normalizeAgentWorkbenchPath(project.path) }
    val pathRequests = buildArchivedPathRequests(entries)
    val knownPaths = pathRequests.mapTo(LinkedHashSet()) { it.path }
    val sources = sessionSourcesProvider().filter { source -> source.supportsArchivedThreads }
    val cliAvailabilityByProvider = resolveArchivedCliAvailabilityByProvider(sources)
    val availableSources = sources.filter { source -> cliAvailabilityByProvider[source.provider] != false }
    val loadingProviderLoadStates = buildLoadingProviderLoadStates(availableSources.map { source -> source.provider })
    val initialProjects = buildInitialArchivedProjects(
      entries = entries,
      previousProjectsByPath = previousProjectsByPath,
      loadingProviderLoadStates = loadingProviderLoadStates,
    )

    mutableState.update { state ->
      state.copy(
        projects = initialProjects,
        visibleThreadCounts = retainVisibleThreadCounts(knownPaths, state.visibleThreadCounts),
      )
    }

    val resultsByPath = coroutineScope {
      pathRequests.map { request ->
        async {
          request.path to loadArchivedThreads(path = request.path, project = request.project, sources = availableSources)
        }
      }.awaitAll().toMap()
    }

    mutableState.update { state ->
      state.copy(
        projects = applyArchivedResults(state.projects, resultsByPath),
        lastUpdatedAt = System.currentTimeMillis(),
      )
    }
  }

  private suspend fun loadArchivedThreads(
    path: String,
    project: Project?,
    sources: List<AgentSessionSource>,
  ): AgentSessionLoadResult {
    if (sources.isEmpty()) {
      return AgentSessionLoadResult(threads = emptyList())
    }
    val sourceResults = coroutineScope {
      sources.map { source ->
        async {
          val result = try {
            Result.success(
              if (project != null) {
                source.listArchivedThreadsFromOpenProject(path = path, project = project)
              }
              else {
                source.listArchivedThreadsFromClosedProject(path = path)
              }
            )
          }
          catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            if (isCliMissingError(source.provider, throwable)) return@async null
            ARCHIVED_LOG.warn("Failed to load archived ${source.provider.value} sessions for $path", throwable)
            Result.failure(throwable)
          }
          AgentSessionSourceLoadResult(
            provider = source.provider,
            result = result,
            hasUnknownTotal = false,
          )
        }
      }.awaitAll().filterNotNull()
    }
    return mergeAgentSessionSourceLoadResults(
      sourceResults = sourceResults,
      resolveErrorMessage = ::resolveArchivedErrorMessage,
      resolveWarningMessage = ::resolveArchivedProviderWarningMessage,
    )
  }

  private suspend fun resolveArchivedCliAvailabilityByProvider(
    sources: List<AgentSessionSource>,
  ): Map<AgentSessionProvider, Boolean> {
    return withContext(Dispatchers.IO) {
      coroutineScope {
        sources.map { source ->
          async {
            val descriptor = AgentSessionProviders.find(source.provider) ?: return@async source.provider to true
            source.provider to try {
              AgentSessionProviderCliAvailabilityCache.resolveAvailability(descriptor, force = false) {
                descriptor.isCliAvailable()
              }
            }
            catch (e: CancellationException) {
              throw e
            }
            catch (throwable: Throwable) {
              ARCHIVED_LOG.warn("Failed to resolve CLI availability for ${source.provider.value}", throwable)
              false
            }
          }
        }.awaitAll().toMap()
      }
    }
  }

  private fun hydrateVisibleThreadCosts(state: AgentArchivedSessionsState) {
    val visibleThreads = collectVisibleThreads(state)
    if (visibleThreads.isEmpty()) {
      return
    }

    val sourcesByProvider = sessionSourcesProvider()
      .asSequence()
      .filter(AgentSessionSource::supportsArchivedThreads)
      .associateBy(AgentSessionSource::provider)
    val cachedUpdatesByPath = LinkedHashMap<String, MutableMap<AgentSessionProvider, MutableMap<String, ArchivedThreadCostUpdate>>>()
    val loadRequests = LinkedHashMap<Pair<AgentSessionSource, String>, MutableList<ArchivedVisibleThreadSnapshot>>()

    for (visibleThread in visibleThreads) {
      if (normalizeConcreteAgentSessionThreadId(visibleThread.threadId) == null) {
        continue
      }

      val cacheKey = visibleThread.cacheKey
      val cacheEntry = costCache[cacheKey]
      if (visibleThread.cost != null && (cacheEntry == null || cacheEntry.updatedAt != visibleThread.updatedAt)) {
        costCache[cacheKey] = ArchivedThreadCostCacheEntry(
          updatedAt = visibleThread.updatedAt,
          cost = visibleThread.cost,
        )
      }

      val effectiveCacheEntry = costCache[cacheKey]
      if (effectiveCacheEntry != null && effectiveCacheEntry.updatedAt == visibleThread.updatedAt) {
        if (visibleThread.cost != effectiveCacheEntry.cost) {
          cachedUpdatesByPath
            .getOrPut(visibleThread.path) { LinkedHashMap() }
            .getOrPut(visibleThread.provider) { LinkedHashMap() }[visibleThread.threadId] = ArchivedThreadCostUpdate(
            expectedUpdatedAt = visibleThread.updatedAt,
            cost = effectiveCacheEntry.cost,
          )
        }
        continue
      }

      val source = sourcesByProvider[visibleThread.provider] ?: continue
      val loadKey = visibleThread.loadKey
      if (!inFlightCostLoads.add(loadKey)) {
        continue
      }
      loadRequests.getOrPut(source to visibleThread.path) { ArrayList() }.add(visibleThread)
    }

    applyArchivedThreadCostUpdates(cachedUpdatesByPath)

    for ((requestKey, requestedThreads) in loadRequests) {
      val (source, path) = requestKey
      serviceScope.launch(Dispatchers.IO) {
        try {
          val requestedAgentThreads = requestedThreads.map(ArchivedVisibleThreadSnapshot::thread)
          val loadedCostsByThreadId = source.loadThreadCosts(path = path, threads = requestedAgentThreads)
          val updatesByProvider = LinkedHashMap<AgentSessionProvider, MutableMap<String, ArchivedThreadCostUpdate>>()
          for (visibleThread in requestedThreads) {
            val loadedCost = loadedCostsByThreadId[visibleThread.threadId]
            costCache[visibleThread.cacheKey] = ArchivedThreadCostCacheEntry(
              updatedAt = visibleThread.updatedAt,
              cost = loadedCost,
            )
            updatesByProvider
              .getOrPut(visibleThread.provider) { LinkedHashMap() }[visibleThread.threadId] = ArchivedThreadCostUpdate(
              expectedUpdatedAt = visibleThread.updatedAt,
              cost = loadedCost,
            )
          }
          applyArchivedThreadCostUpdates(mapOf(path to updatesByProvider))
        }
        catch (t: Throwable) {
          ARCHIVED_LOG.debug(t) {
            "Failed to hydrate archived visible thread costs for ${source.provider.value} path=$path threads=${requestedThreads.size}"
          }
        }
        finally {
          requestedThreads.forEach { visibleThread ->
            inFlightCostLoads.remove(visibleThread.loadKey)
          }
        }
      }
    }
  }

  private fun applyArchivedThreadCostUpdates(
    updatesByPath: Map<String, Map<AgentSessionProvider, Map<String, ArchivedThreadCostUpdate>>>,
  ) {
    if (updatesByPath.isEmpty()) {
      return
    }

    mutableState.update { state ->
      var changed = false
      val nextProjects = state.projects.map { project ->
        val projectUpdates = updatesByPath[project.path]
        val nextProjectThreads = if (projectUpdates != null) {
          updateArchivedThreadCosts(project.threads, projectUpdates).also { updatedThreads ->
            if (updatedThreads !== project.threads) changed = true
          }
        }
        else {
          project.threads
        }
        val nextWorktrees = project.worktrees.map { worktree ->
          val worktreeUpdates = updatesByPath[worktree.path]
          val nextThreads = if (worktreeUpdates != null) {
            updateArchivedThreadCosts(worktree.threads, worktreeUpdates).also { updatedThreads ->
              if (updatedThreads !== worktree.threads) changed = true
            }
          }
          else {
            worktree.threads
          }
          if (nextThreads === worktree.threads) worktree else worktree.copy(threads = nextThreads)
        }
        if (nextProjectThreads === project.threads && nextWorktrees == project.worktrees) {
          project
        }
        else {
          project.copy(threads = nextProjectThreads, worktrees = nextWorktrees)
        }
      }
      if (changed) state.copy(projects = nextProjects, lastUpdatedAt = System.currentTimeMillis()) else state
    }
  }

  private fun collectVisibleThreads(state: AgentArchivedSessionsState): List<ArchivedVisibleThreadSnapshot> {
    val visibleThreads = ArrayList<ArchivedVisibleThreadSnapshot>()
    state.projects.forEach { project ->
      collectVisibleThreadsForPath(
        path = project.path,
        threads = project.threads,
        visibleThreadCounts = state.visibleThreadCounts,
        collector = visibleThreads,
      )
      project.worktrees.forEach { worktree ->
        collectVisibleThreadsForPath(
          path = worktree.path,
          threads = worktree.threads,
          visibleThreadCounts = state.visibleThreadCounts,
          collector = visibleThreads,
        )
      }
    }
    return visibleThreads
  }

  private fun collectVisibleThreadsForPath(
    path: String,
    threads: List<AgentSessionThread>,
    visibleThreadCounts: Map<String, Int>,
    collector: MutableList<ArchivedVisibleThreadSnapshot>,
  ) {
    val visibleCount = visibleThreadCounts[path] ?: DEFAULT_VISIBLE_THREAD_COUNT
    threads.asSequence()
      .take(visibleCount)
      .forEach { thread ->
        collector.add(
          ArchivedVisibleThreadSnapshot(
            path = path,
            provider = thread.provider,
            thread = thread,
          )
        )
      }
  }
}

private data class ArchivedPathRequest(
  @JvmField val path: String,
  @JvmField val project: Project?,
)

private fun buildArchivedPathRequests(entries: List<ProjectEntry>): List<ArchivedPathRequest> {
  val requests = ArrayList<ArchivedPathRequest>()
  val seenPaths = LinkedHashSet<String>()

  fun add(path: String, project: Project?) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    if (seenPaths.add(normalizedPath)) {
      requests.add(ArchivedPathRequest(path = normalizedPath, project = project))
    }
  }

  entries.forEach { entry ->
    add(entry.path, entry.project)
    entry.worktreeEntries.forEach { worktree -> add(worktree.path, worktree.project) }
  }
  return requests
}

private fun buildInitialArchivedProjects(
  entries: List<ProjectEntry>,
  previousProjectsByPath: Map<String, AgentProjectSessions>,
  loadingProviderLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState>,
): List<AgentProjectSessions> {
  return entries.map { entry ->
    val normalizedPath = normalizeAgentWorkbenchPath(entry.path)
    val previous = previousProjectsByPath[normalizedPath]
    AgentProjectSessions(
      path = normalizedPath,
      name = entry.name,
      branch = entry.branch,
      buildSystemBadge = entry.buildSystemBadge,
      isOpen = entry.project != null,
      threads = previous?.threads.orEmpty(),
      errorMessage = null,
      providerWarnings = emptyList(),
      providerLoadStates = mergeProviderLoadStates(previous?.providerLoadStates.orEmpty(), loadingProviderLoadStates),
      providersWithUnknownThreadCount = previous?.providersWithUnknownThreadCount.orEmpty() - loadingProviderLoadStates.keys,
      worktrees = entry.worktreeEntries.map { worktree ->
        val normalizedWorktreePath = normalizeAgentWorkbenchPath(worktree.path)
        val previousWorktree = previous?.worktrees?.firstOrNull { candidate -> candidate.path == normalizedWorktreePath }
        AgentWorktree(
          path = normalizedWorktreePath,
          name = worktree.name,
          branch = worktree.branch,
          isOpen = worktree.project != null,
          threads = previousWorktree?.threads.orEmpty(),
          errorMessage = null,
          providerWarnings = emptyList(),
          providerLoadStates = mergeProviderLoadStates(previousWorktree?.providerLoadStates.orEmpty(), loadingProviderLoadStates),
          providersWithUnknownThreadCount = previousWorktree?.providersWithUnknownThreadCount.orEmpty() - loadingProviderLoadStates.keys,
        )
      },
    )
  }
}

private fun applyArchivedResults(
  projects: List<AgentProjectSessions>,
  resultsByPath: Map<String, AgentSessionLoadResult>,
): List<AgentProjectSessions> {
  return projects.map { project ->
    val result = resultsByPath[project.path]
    val refreshedThreads = preserveThreadCosts(project.threads, result?.threads.orEmpty())
    project.copy(
      threads = refreshedThreads,
      errorMessage = result?.errorMessage,
      providerWarnings = result?.providerWarnings ?: emptyList(),
      providerLoadStates = result?.providerLoadStates ?: emptyMap(),
      providersWithUnknownThreadCount = result?.providersWithUnknownThreadCount ?: emptySet(),
      worktrees = project.worktrees.map { worktree ->
        val worktreeResult = resultsByPath[worktree.path]
        val refreshedWorktreeThreads = preserveThreadCosts(worktree.threads, worktreeResult?.threads.orEmpty())
        worktree.copy(
          threads = refreshedWorktreeThreads,
          errorMessage = worktreeResult?.errorMessage,
          providerWarnings = worktreeResult?.providerWarnings ?: emptyList(),
          providerLoadStates = worktreeResult?.providerLoadStates ?: emptyMap(),
          providersWithUnknownThreadCount = worktreeResult?.providersWithUnknownThreadCount ?: emptySet(),
        )
      },
    )
  }
}

private fun retainVisibleThreadCounts(knownPaths: Set<String>, visibleThreadCounts: Map<String, Int>): Map<String, Int> {
  val result = LinkedHashMap<String, Int>()
  visibleThreadCounts.forEach { (path, count) ->
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    if (normalizedPath in knownPaths && count > DEFAULT_VISIBLE_THREAD_COUNT) {
      result[normalizedPath] = count
    }
  }
  return result
}

private fun updateArchivedThreadCosts(
  threads: List<AgentSessionThread>,
  updatesByProvider: Map<AgentSessionProvider, Map<String, ArchivedThreadCostUpdate>>,
): List<AgentSessionThread> {
  var changed = false
  val updatedThreads = threads.map { thread ->
    val providerUpdates = updatesByProvider[thread.provider] ?: return@map thread
    val costUpdate = providerUpdates[thread.id] ?: return@map thread
    if (thread.updatedAt != costUpdate.expectedUpdatedAt || thread.cost == costUpdate.cost) {
      return@map thread
    }
    changed = true
    thread.copy(cost = costUpdate.cost)
  }
  return if (changed) updatedThreads else threads
}

private fun List<AgentProjectSessions>.anyPathLoading(): Boolean {
  return any { project -> project.isLoading || project.worktrees.any(AgentWorktree::isLoading) }
}

private fun resolveArchivedErrorMessage(provider: AgentSessionProvider, throwable: Throwable): String {
  return if (isCliMissingError(provider, throwable)) {
    AgentSessionsBundle.message(agentSessionCliMissingMessageKey(provider))
  }
  else {
    AgentSessionsBundle.message("toolwindow.error")
  }
}

private fun resolveArchivedProviderWarningMessage(provider: AgentSessionProvider, throwable: Throwable): String {
  return if (isCliMissingError(provider, throwable)) {
    AgentSessionsBundle.message(agentSessionCliMissingMessageKey(provider))
  }
  else {
    AgentSessionsBundle.message("toolwindow.warning.provider.unavailable", resolveProviderLabel(provider))
  }
}

private fun isCliMissingError(provider: AgentSessionProvider, throwable: Throwable): Boolean {
  return AgentSessionProviders.find(provider)?.isCliMissingError(throwable) == true
}

private fun resolveProviderLabel(provider: AgentSessionProvider): String {
  val descriptor = AgentSessionProviders.find(provider)
  return if (descriptor != null) AgentSessionsBundle.message(descriptor.displayNameKey) else provider.value
}

private data class ArchivedThreadCostUpdate(
  @JvmField val expectedUpdatedAt: Long,
  @JvmField val cost: AgentSessionCost?,
)

private data class ArchivedVisibleThreadSnapshot(
  @JvmField val path: String,
  val provider: AgentSessionProvider,
  @JvmField val thread: AgentSessionThread,
) {
  val threadId: String
    get() = thread.id

  val updatedAt: Long
    get() = thread.updatedAt

  val cost: AgentSessionCost?
    get() = thread.cost

  val cacheKey: ArchivedThreadCacheKey
    get() = ArchivedThreadCacheKey(path = path, provider = provider, threadId = threadId)

  val loadKey: ArchivedThreadLoadKey
    get() = ArchivedThreadLoadKey(path = path, provider = provider, threadId = threadId, updatedAt = updatedAt)
}

private data class ArchivedThreadCacheKey(
  @JvmField val path: String,
  val provider: AgentSessionProvider,
  @JvmField val threadId: String,
)

private data class ArchivedThreadLoadKey(
  @JvmField val path: String,
  val provider: AgentSessionProvider,
  @JvmField val threadId: String,
  @JvmField val updatedAt: Long,
)

private data class ArchivedThreadCostCacheEntry(
  @JvmField val updatedAt: Long,
  @JvmField val cost: AgentSessionCost?,
)

@Suppress("DuplicatedCode")
private fun findThreadIndex(
  projects: List<AgentProjectSessions>,
  normalizedPath: String,
  provider: AgentSessionProvider,
  threadId: String,
): Int? {
  val projectThreads = projects.firstOrNull { it.path == normalizedPath }?.threads
  if (projectThreads != null) {
    val index =
      projectThreads.indexOfFirst { thread -> thread.matchesProviderAndThreadOrSubAgent(provider = provider, threadId = threadId) }
    if (index >= 0) return index
  }

  projects.forEach { project ->
    val worktreeThreads = project.worktrees.firstOrNull { it.path == normalizedPath }?.threads ?: return@forEach
    val index =
      worktreeThreads.indexOfFirst { thread -> thread.matchesProviderAndThreadOrSubAgent(provider = provider, threadId = threadId) }
    if (index >= 0) return index
  }

  return null
}

private fun AgentSessionThread.matchesProviderAndThreadOrSubAgent(provider: AgentSessionProvider, threadId: String): Boolean {
  return this.provider == provider && (id == threadId || subAgents.any { subAgent -> subAgent.id == threadId })
}

@Suppress("DuplicatedCode")
private fun requiredVisibleClosedProjectCount(projects: List<AgentProjectSessions>, normalizedPath: String): Int? {
  var closedProjectCount = 0
  projects.forEach { project ->
    val isAlwaysVisible = project.isOpen || project.worktrees.any { worktree -> worktree.isOpen }
    if (!isAlwaysVisible) {
      closedProjectCount++
    }
    if (normalizeAgentWorkbenchPath(project.path) == normalizedPath) {
      return if (isAlwaysVisible) 0 else closedProjectCount
    }
  }
  return null
}
