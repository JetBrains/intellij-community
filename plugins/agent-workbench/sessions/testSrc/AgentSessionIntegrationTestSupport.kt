// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatConcreteTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatOpenTabsRefreshSnapshot
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatPendingTabSnapshot
import com.intellij.agent.workbench.chat.collectOpenConcreteAgentChatThreadIdentitiesByPath
import com.intellij.agent.workbench.chat.collectOpenPendingAgentChatTabsByPath
import com.intellij.agent.workbench.chat.rebindOpenPendingAgentChatTabs
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.session.AgentSessionCost
import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.core.session.AgentSubAgent
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionArchivedSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionCostSource
import com.intellij.platform.ai.agent.sessions.core.providers.BaseAgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionPrefetchSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRefreshHintsSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRefreshSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceRefreshRequest
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceRefreshResult
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionThreadActivityUpdate
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionUpdateSource
import com.intellij.agent.workbench.sessions.frame.AgentChatOpenModeSettings
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.ProjectEntry
import com.intellij.agent.workbench.sessions.model.WorktreeEntry
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveBackgroundTaskRunner
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveService
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveTransitionSuppressions
import com.intellij.agent.workbench.sessions.service.AgentSessionChatOpenExecutor
import com.intellij.agent.workbench.sessions.service.AgentSessionContentRepository
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchProfileResolverImpl
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.service.AgentSessionRefreshService
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.state.InMemorySessionWarmState
import com.intellij.agent.workbench.sessions.state.SessionWarmState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.Project
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.time.Duration.Companion.milliseconds

internal const val PROJECT_PATH = "/work/project-a"
internal const val WORKTREE_PATH = "/work/project-feature"

internal fun buildOpenChatRefreshSnapshot(
  openProjectPaths: Set<String> = emptySet(),
  selectedChatThreadIdentity: Pair<AgentSessionProvider, String>? = null,
  pendingTabsByProvider: Map<AgentSessionProvider, Map<String, List<AgentChatPendingTabSnapshot>>> = emptyMap(),
  concreteTabsAwaitingNewThreadRebindByProvider: Map<AgentSessionProvider, Map<String, List<AgentChatConcreteTabSnapshot>>> = emptyMap(),
  concreteThreadIdentitiesByPath: Map<String, Set<String>> = emptyMap(),
): AgentChatOpenTabsRefreshSnapshot {
  val normalizedOpenProjectPaths = LinkedHashSet<String>()
  openProjectPaths.asSequence().map(::normalizeAgentWorkbenchPath).forEach(normalizedOpenProjectPaths::add)

  val normalizedPendingTabsByProvider = pendingTabsByProvider.mapValues { (_, tabsByPath) ->
    normalizeSnapshotTabsByPath(tabsByPath)
  }
  normalizedPendingTabsByProvider.values
    .asSequence()
    .flatMap { tabsByPath -> tabsByPath.keys.asSequence() }
    .forEach(normalizedOpenProjectPaths::add)

  val normalizedConcreteTabsByProvider = concreteTabsAwaitingNewThreadRebindByProvider.mapValues { (_, tabsByPath) ->
    normalizeSnapshotTabsByPath(tabsByPath)
  }
  normalizedConcreteTabsByProvider.values
    .asSequence()
    .flatMap { tabsByPath -> tabsByPath.keys.asSequence() }
    .forEach(normalizedOpenProjectPaths::add)

  val normalizedConcreteThreadIdentitiesByPath = LinkedHashMap<String, Set<String>>(concreteThreadIdentitiesByPath.size)
  for ((path, identities) in concreteThreadIdentitiesByPath) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    normalizedConcreteThreadIdentitiesByPath[normalizedPath] = LinkedHashSet(identities)
    normalizedOpenProjectPaths.add(normalizedPath)
  }

  return AgentChatOpenTabsRefreshSnapshot(
    openProjectPaths = normalizedOpenProjectPaths,
    selectedChatThreadIdentity = selectedChatThreadIdentity,
    pendingTabsByProvider = normalizedPendingTabsByProvider,
    concreteTabsAwaitingNewThreadRebindByProvider = normalizedConcreteTabsByProvider,
    concreteThreadIdentitiesByPath = normalizedConcreteThreadIdentitiesByPath,
  )
}

private fun <T> normalizeSnapshotTabsByPath(
  tabsByPath: Map<String, List<T>>,
): Map<String, List<T>> {
  val normalized = LinkedHashMap<String, List<T>>(tabsByPath.size)
  for ((path, tabs) in tabsByPath) {
    if (tabs.isEmpty()) {
      continue
    }
    normalized[normalizeAgentWorkbenchPath(path)] = tabs
  }
  return normalized
}

data class TestProjectCatalogEntry(
  @JvmField val path: String,
  @JvmField val name: String,
  @JvmField val worktrees: List<TestWorktreeCatalogEntry> = emptyList(),
  @JvmField val branch: String? = null,
  @JvmField val isOpen: Boolean = true,
  @JvmField val projectDirectory: String? = null,
)

data class TestWorktreeCatalogEntry(
  @JvmField val path: String,
  @JvmField val name: String,
  @JvmField val branch: String?,
  @JvmField val isOpen: Boolean = false,
  @JvmField val projectDirectory: String? = null,
)

class AgentSessionStateSyncTestFacade(
  private val stateStore: AgentSessionsStateStore,
  private val syncService: AgentSessionRefreshService,
) {
  val state: StateFlow<AgentSessionsState>
    get() = stateStore.state

  fun refresh() {
    syncService.refresh()
  }

  fun refreshCatalogAndLoadNewlyOpened() {
    syncService.refreshCatalogAndLoadNewlyOpened()
  }

  fun refreshProviderForPath(path: String, provider: AgentSessionProvider) {
    syncService.refreshProviderForPath(path = path, provider = provider)
  }

  fun rebindPendingTabsInBackground(
    provider: AgentSessionProvider,
    requestsByProjectPath: Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) {
    syncService.rebindPendingTabsInBackground(provider = provider, requestsByProjectPath = requestsByProjectPath)
  }

  fun markThreadAsRead(path: String, provider: AgentSessionProvider, threadId: String, updatedAt: Long) {
    syncService.markThreadAsRead(path = path, provider = provider, threadId = threadId, updatedAt = updatedAt)
  }

  fun showMoreThreads(path: String) {
    stateStore.showMoreThreads(path)
  }

  fun ensureProjectVisible(path: String) {
    stateStore.ensureProjectVisible(path)
  }

  fun ensureThreadVisible(path: String, provider: AgentSessionProvider, threadId: String) {
    stateStore.ensureThreadVisible(path = path, provider = provider, threadId = threadId)
  }

  fun loadProjectThreadsOnDemand(path: String) {
    syncService.loadProjectThreadsOnDemand(path)
  }

  fun loadWorktreeThreadsOnDemand(projectPath: String, worktreePath: String) {
    syncService.loadWorktreeThreadsOnDemand(projectPath = projectPath, worktreePath = worktreePath)
  }
}

class ScriptedSessionSource(
  override val provider: AgentSessionProvider,
  override val canReportExactThreadCount: Boolean = true,
  private val supportsArchivedThreads: Boolean = false,
  supportsUpdates: Boolean = false,
  updateEvents: Flow<AgentSessionSourceUpdateEvent> = emptyFlow(),
  private val listFromOpenProject: suspend (path: String, project: Project) -> List<AgentSessionThread> = { _, _ -> emptyList() },
  private val listFromClosedProject: suspend (path: String) -> List<AgentSessionThread> = { _ -> emptyList() },
  private val listArchivedFromOpenProject: suspend (path: String, project: Project) -> List<AgentSessionThread> = { _, _ -> emptyList() },
  private val listArchivedFromClosedProject: suspend (path: String) -> List<AgentSessionThread> = { _ -> emptyList() },
  private val prefetch: suspend (paths: List<String>) -> Map<String, List<AgentSessionThread>> = { emptyMap() },
  private val refreshThreadsProvider: (suspend (AgentSessionSourceRefreshRequest) -> AgentSessionSourceRefreshResult)? = null,
  private val loadThreadCostsProvider: suspend (path: String, threads: List<AgentSessionThread>) -> Map<String, AgentSessionCost?> = { _, _ -> emptyMap() },
  private val prefetchRefreshHintsProvider: suspend (
    paths: List<String>,
    knownThreadIdsByPath: Map<String, Set<String>>,
  ) -> Map<String, AgentSessionRefreshHints> = { _, _ -> emptyMap() },
  private val prefetchRefreshThreadSeedsProvider: suspend (
    paths: List<String>,
    refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
  ) -> Map<String, AgentSessionRefreshHints> = { paths, refreshThreadSeedsByPath ->
    prefetchRefreshHintsProvider(
      paths,
      refreshThreadSeedsByPath.mapValues { (_, refreshThreadSeeds) ->
        refreshThreadSeeds.asSequence().map { refreshThreadSeed -> refreshThreadSeed.threadId }.toCollection(LinkedHashSet())
      }
    )
  },
) : AgentSessionSource,
    AgentSessionArchivedSource,
    AgentSessionUpdateSource,
    AgentSessionPrefetchSource,
    AgentSessionRefreshSource,
    AgentSessionRefreshHintsSource,
    AgentSessionCostSource {
  override val updateEvents: Flow<AgentSessionSourceUpdateEvent> = if (supportsUpdates) updateEvents else emptyFlow()

  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    return if (openProject == null) listFromClosedProject(path) else listFromOpenProject(path, openProject)
  }

  override suspend fun listArchivedThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    if (!supportsArchivedThreads) {
      return emptyList()
    }
    return if (openProject == null) listArchivedFromClosedProject(path) else listArchivedFromOpenProject(path, openProject)
  }

  override suspend fun prefetchThreads(paths: List<String>): Map<String, List<AgentSessionThread>> {
    return prefetch(paths)
  }

  override suspend fun refreshThreads(request: AgentSessionSourceRefreshRequest): AgentSessionSourceRefreshResult {
    val refreshResult = refreshThreadsProvider?.invoke(request)
    if (refreshResult != null) {
      return refreshResult
    }
    val prefetchedThreadsByPath = prefetch(request.sourcePaths())
    val completeThreadsByPath = LinkedHashMap<String, List<AgentSessionThread>>(request.paths.size)
    val failuresByPath = LinkedHashMap<String, Throwable>()
    for (path in request.paths) {
      val sourcePath = request.sourcePathFor(path)
      val prefetchedThreads = prefetchedThreadsByPath[sourcePath]
      if (prefetchedThreads != null) {
        completeThreadsByPath[path] = prefetchedThreads
        continue
      }
      try {
        completeThreadsByPath[path] = listFromClosedProject(sourcePath)
      }
      catch (e: Throwable) {
        if (e is CancellationException) throw e
        failuresByPath[path] = e
      }
    }
    return AgentSessionSourceRefreshResult(
      completeThreadsByPath = completeThreadsByPath,
      failuresByPath = failuresByPath,
    )
  }

  override suspend fun loadThreadCosts(path: String, threads: List<AgentSessionThread>): Map<String, AgentSessionCost?> {
    return loadThreadCostsProvider(path, threads)
  }

  override suspend fun prefetchRefreshHints(
    paths: List<String>,
    refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
  ): Map<String, AgentSessionRefreshHints> {
    return prefetchRefreshThreadSeedsProvider(paths, refreshThreadSeedsByPath)
  }
}

class CwdBackedScriptedSessionSource(
  provider: AgentSessionProvider,
  canReportExactThreadCount: Boolean = true,
  private val list: suspend (path: String, openProject: Project?) -> List<AgentSessionThread> = { _, _ -> emptyList() },
  private val listArchived: suspend (path: String, openProject: Project?) -> List<AgentSessionThread> = { _, _ -> emptyList() },
  private val prefetch: suspend (paths: List<String>) -> Map<String, List<AgentSessionThread>> = { emptyMap() },
) : BaseAgentSessionSource(
  provider = provider,
  canReportExactThreadCount = canReportExactThreadCount,
), AgentSessionArchivedSource, AgentSessionPrefetchSource {
  override suspend fun loadThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    return list(path, openProject)
  }

  override suspend fun listArchivedThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    return listArchived(path, openProject)
  }

  override suspend fun prefetchThreads(paths: List<String>): Map<String, List<AgentSessionThread>> {
    return prefetch(paths)
  }
}

fun threadsChangedEvent(
  scopedPaths: Set<String>? = null,
  threadIds: Set<String>? = null,
  activityUpdatesByThreadId: Map<String, AgentSessionThreadActivityUpdate> = emptyMap(),
  mayHaveChangedProjectFiles: Boolean = false,
  changedProjectFilePaths: Set<String>? = null,
): AgentSessionSourceUpdateEvent {
  return AgentSessionSourceUpdateEvent.threadsChanged(
    scopedPaths = scopedPaths,
    threadIds = threadIds,
    activityUpdatesByThreadId = activityUpdatesByThreadId,
    mayHaveChangedProjectFiles = mayHaveChangedProjectFiles,
    changedProjectFilePaths = changedProjectFilePaths,
  )
}

fun hintsChangedEvent(
  scopedPaths: Set<String>? = null,
  threadIds: Set<String>? = null,
  activityUpdatesByThreadId: Map<String, AgentSessionThreadActivityUpdate> = emptyMap(),
  mayHaveChangedProjectFiles: Boolean = false,
  changedProjectFilePaths: Set<String>? = null,
): AgentSessionSourceUpdateEvent {
  return AgentSessionSourceUpdateEvent.hintsChanged(
    scopedPaths = scopedPaths,
    threadIds = threadIds,
    activityUpdatesByThreadId = activityUpdatesByThreadId,
    mayHaveChangedProjectFiles = mayHaveChangedProjectFiles,
    changedProjectFilePaths = changedProjectFilePaths,
  )
}

fun activityUpdate(
  activity: AgentThreadActivity,
  chromeActivity: AgentThreadActivity? = activity,
  updatesChromeActivity: Boolean = true,
  updatedAt: Long? = null,
): AgentSessionThreadActivityUpdate {
  return AgentSessionThreadActivityUpdate(
    activityReport = AgentThreadActivityReport(rowActivity = activity, chromeActivity = chromeActivity),
    updatesChromeActivity = updatesChromeActivity,
    updatedAt = updatedAt,
  )
}

fun thread(
  id: String,
  updatedAt: Long,
  provider: AgentSessionProvider,
  title: String = id,
  activity: AgentThreadActivity = AgentThreadActivity.READY,
  summaryActivity: AgentThreadActivity? = activity,
  subAgents: List<AgentSubAgent> = emptyList(),
  cost: AgentSessionCost? = null,
): AgentSessionThread {
  return AgentSessionThread(
    id = id,
    title = title,
    updatedAt = updatedAt,
    archived = false,
    provider = provider,
    activity = activity,
    summaryActivity = summaryActivity,
    subAgents = subAgents,
    cost = cost,
  )
}

suspend fun withRegisteredTestService(
  parentDisposable: com.intellij.openapi.Disposable,
  sessionSourcesProvider: () -> List<AgentSessionSource>,
  projectEntriesProvider: suspend () -> List<TestProjectCatalogEntry>,
  toolWindowVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
  action: suspend (AgentSessionStateSyncTestFacade) -> Unit,
) {
  val job = SupervisorJob()

  @Suppress("RAW_SCOPE_CREATION")
  val scope = CoroutineScope(job + Dispatchers.Default)
  val stateStore = AgentSessionsStateStore()
  val warmState = InMemorySessionWarmState()
  val syncService = AgentSessionRefreshService(
    serviceScope = scope,
    sessionSourcesProvider = sessionSourcesProvider,
    projectEntriesProvider = { projectEntriesProvider().map { it.toProjectEntry() } },
    stateStore = stateStore,
    warmState = warmState,
    scheduleVfsRefresh = { _ -> },
    openAgentChatSnapshotProvider = { buildOpenChatRefreshSnapshot() },
    providerDescriptorProvider = { provider -> testIntegrationProviderDescriptor(provider) },
    toolWindowVisibleFlow = toolWindowVisibleFlow,
    loadingDelayMs = 0L,
    subscribeToProjectLifecycle = false,
  )
  val app = ApplicationManager.getApplication()
  app.replaceService(AgentSessionsStateStore::class.java, stateStore, parentDisposable)
  app.replaceService(AgentSessionRefreshService::class.java, syncService, parentDisposable)
  try {
    action(
      AgentSessionStateSyncTestFacade(
        stateStore = stateStore,
        syncService = syncService,
      )
    )
  }
  finally {
    job.cancelAndJoin()
  }
}

suspend fun withTestService(
  sessionSourcesProvider: () -> List<AgentSessionSource>,
  projectEntriesProvider: suspend () -> List<TestProjectCatalogEntry>,
  action: suspend (AgentSessionStateSyncTestFacade) -> Unit,
) {
  withService(
    sessionSourcesProvider = sessionSourcesProvider,
    projectEntriesProvider = { projectEntriesProvider().map { it.toProjectEntry() } },
    action = action,
  )
}

internal suspend fun withTestServiceAndLaunch(
  sessionSourcesProvider: () -> List<AgentSessionSource>,
  projectEntriesProvider: suspend () -> List<TestProjectCatalogEntry>,
  warmState: SessionWarmState = InMemorySessionWarmState(),
  uiPreferencesState: AgentSessionUiPreferencesStateService = AgentSessionUiPreferencesStateService(),
  chatOpenExecutor: AgentSessionChatOpenExecutor? = null,
  openPendingCodexTabsProvider: suspend () -> Map<String, List<AgentChatPendingTabSnapshot>> =
    ::collectOpenPendingCodexTabsByPath,
  openConcreteChatThreadIdentitiesByPathProvider: suspend () -> Map<String, Set<String>> =
    ::collectOpenConcreteAgentChatThreadIdentitiesByPath,
  openAgentChatPendingTabsBinder: suspend (
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = ::rebindOpenPendingCodexTabs,
  openPendingAgentChatTabsProvider: suspend (AgentSessionProvider) -> Map<String, List<AgentChatPendingTabSnapshot>> = { provider ->
    if (provider == AgentSessionProvider.from("codex")) openPendingCodexTabsProvider() else collectOpenPendingAgentChatTabsByPath(provider)
  },
  openAgentChatPendingTabsBinderWithProvider: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = { _, requestsByPath -> openAgentChatPendingTabsBinder(requestsByPath) },
  archivedSessionsRefreshIfLoaded: () -> Unit = {},
  toolWindowVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
  currentTimeMillis: () -> Long = System::currentTimeMillis,
  loadingDelayMs: Long = 0L,
  visibleCostHydrationDelayMs: Long = 750L,
  workingThreadCostCacheTtlMs: Long = 60_000L,
  branchMismatchConfirmation: suspend (Project?, String, String) -> Boolean = { _, _, _ ->
    error("Unexpected branch mismatch confirmation")
  },
  action: suspend (AgentSessionStateSyncTestFacade, AgentSessionLaunchService) -> Unit,
) {
  withServiceAndLaunch(
    sessionSourcesProvider = sessionSourcesProvider,
    projectEntriesProvider = { projectEntriesProvider().map { it.toProjectEntry() } },
    warmState = warmState,
    uiPreferencesState = uiPreferencesState,
    chatOpenExecutor = chatOpenExecutor,
    openPendingCodexTabsProvider = openPendingCodexTabsProvider,
    openConcreteChatThreadIdentitiesByPathProvider = openConcreteChatThreadIdentitiesByPathProvider,
    openAgentChatPendingTabsBinder = openAgentChatPendingTabsBinder,
    openPendingAgentChatTabsProvider = openPendingAgentChatTabsProvider,
    openAgentChatPendingTabsBinderWithProvider = openAgentChatPendingTabsBinderWithProvider,
    archivedSessionsRefreshIfLoaded = archivedSessionsRefreshIfLoaded,
    toolWindowVisibleFlow = toolWindowVisibleFlow,
    currentTimeMillis = currentTimeMillis,
    loadingDelayMs = loadingDelayMs,
    visibleCostHydrationDelayMs = visibleCostHydrationDelayMs,
    workingThreadCostCacheTtlMs = workingThreadCostCacheTtlMs,
    branchMismatchConfirmation = branchMismatchConfirmation,
    action = action,
  )
}

internal suspend fun withService(
  sessionSourcesProvider: () -> List<AgentSessionSource>,
  projectEntriesProvider: suspend () -> List<ProjectEntry>,
  warmState: SessionWarmState = InMemorySessionWarmState(),
  uiPreferencesState: AgentSessionUiPreferencesStateService = AgentSessionUiPreferencesStateService(),
  chatOpenExecutor: AgentSessionChatOpenExecutor? = null,
  openPendingCodexTabsProvider: suspend () -> Map<String, List<AgentChatPendingTabSnapshot>> =
    ::collectOpenPendingCodexTabsByPath,
  openConcreteChatThreadIdentitiesByPathProvider: suspend () -> Map<String, Set<String>> =
    ::collectOpenConcreteAgentChatThreadIdentitiesByPath,
  openAgentChatPendingTabsBinder: suspend (
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = ::rebindOpenPendingCodexTabs,
  openPendingAgentChatTabsProvider: suspend (AgentSessionProvider) -> Map<String, List<AgentChatPendingTabSnapshot>> = { provider ->
    if (provider == AgentSessionProvider.from("codex")) openPendingCodexTabsProvider() else collectOpenPendingAgentChatTabsByPath(provider)
  },
  openAgentChatPendingTabsBinderWithProvider: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = { _, requestsByPath -> openAgentChatPendingTabsBinder(requestsByPath) },
  archivedSessionsRefreshIfLoaded: () -> Unit = {},
  toolWindowVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
  currentTimeMillis: () -> Long = System::currentTimeMillis,
  loadingDelayMs: Long = 0L,
  visibleCostHydrationDelayMs: Long = 750L,
  workingThreadCostCacheTtlMs: Long = 60_000L,
  action: suspend (AgentSessionStateSyncTestFacade) -> Unit,
) {
  withServiceAndLaunch(
    sessionSourcesProvider = sessionSourcesProvider,
    projectEntriesProvider = projectEntriesProvider,
    warmState = warmState,
    uiPreferencesState = uiPreferencesState,
    chatOpenExecutor = chatOpenExecutor,
    openPendingCodexTabsProvider = openPendingCodexTabsProvider,
    openConcreteChatThreadIdentitiesByPathProvider = openConcreteChatThreadIdentitiesByPathProvider,
    openAgentChatPendingTabsBinder = openAgentChatPendingTabsBinder,
    openPendingAgentChatTabsProvider = openPendingAgentChatTabsProvider,
    openAgentChatPendingTabsBinderWithProvider = openAgentChatPendingTabsBinderWithProvider,
    archivedSessionsRefreshIfLoaded = archivedSessionsRefreshIfLoaded,
    toolWindowVisibleFlow = toolWindowVisibleFlow,
    currentTimeMillis = currentTimeMillis,
    loadingDelayMs = loadingDelayMs,
    visibleCostHydrationDelayMs = visibleCostHydrationDelayMs,
    workingThreadCostCacheTtlMs = workingThreadCostCacheTtlMs,
  ) { service, _ ->
    action(service)
  }
}

internal suspend fun withServiceAndLaunch(
  sessionSourcesProvider: () -> List<AgentSessionSource>,
  projectEntriesProvider: suspend () -> List<ProjectEntry>,
  warmState: SessionWarmState = InMemorySessionWarmState(),
  uiPreferencesState: AgentSessionUiPreferencesStateService = AgentSessionUiPreferencesStateService(),
  chatOpenExecutor: AgentSessionChatOpenExecutor? = null,
  openPendingCodexTabsProvider: suspend () -> Map<String, List<AgentChatPendingTabSnapshot>> =
    ::collectOpenPendingCodexTabsByPath,
  openConcreteChatThreadIdentitiesByPathProvider: suspend () -> Map<String, Set<String>> =
    ::collectOpenConcreteAgentChatThreadIdentitiesByPath,
  openAgentChatPendingTabsBinder: suspend (
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = ::rebindOpenPendingCodexTabs,
  openPendingAgentChatTabsProvider: suspend (AgentSessionProvider) -> Map<String, List<AgentChatPendingTabSnapshot>> = { provider ->
    if (provider == AgentSessionProvider.from("codex")) openPendingCodexTabsProvider() else collectOpenPendingAgentChatTabsByPath(provider)
  },
  openAgentChatPendingTabsBinderWithProvider: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = { _, requestsByPath -> openAgentChatPendingTabsBinder(requestsByPath) },
  archivedSessionsRefreshIfLoaded: () -> Unit = {},
  toolWindowVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
  currentTimeMillis: () -> Long = System::currentTimeMillis,
  loadingDelayMs: Long = 0L,
  visibleCostHydrationDelayMs: Long = 750L,
  workingThreadCostCacheTtlMs: Long = 60_000L,
  branchMismatchConfirmation: suspend (Project?, String, String) -> Boolean = { _, _, _ ->
    error("Unexpected branch mismatch confirmation")
  },
  action: suspend (AgentSessionStateSyncTestFacade, AgentSessionLaunchService) -> Unit,
) {
  withServiceAndArchiveAndLaunch(
    sessionSourcesProvider = sessionSourcesProvider,
    projectEntriesProvider = projectEntriesProvider,
    warmState = warmState,
    uiPreferencesState = uiPreferencesState,
    archiveChatCleanup = { _, _, _ -> },
    chatOpenExecutor = chatOpenExecutor,
    openPendingCodexTabsProvider = openPendingCodexTabsProvider,
    openConcreteChatThreadIdentitiesByPathProvider = openConcreteChatThreadIdentitiesByPathProvider,
    openAgentChatPendingTabsBinder = openAgentChatPendingTabsBinder,
    openPendingAgentChatTabsProvider = openPendingAgentChatTabsProvider,
    openAgentChatPendingTabsBinderWithProvider = openAgentChatPendingTabsBinderWithProvider,
    archivedSessionsRefreshIfLoaded = archivedSessionsRefreshIfLoaded,
    toolWindowVisibleFlow = toolWindowVisibleFlow,
    currentTimeMillis = currentTimeMillis,
    loadingDelayMs = loadingDelayMs,
    visibleCostHydrationDelayMs = visibleCostHydrationDelayMs,
    workingThreadCostCacheTtlMs = workingThreadCostCacheTtlMs,
    branchMismatchConfirmation = branchMismatchConfirmation,
  ) { service, _, launchService ->
    action(service, launchService)
  }
}

internal suspend fun withServiceAndArchive(
  sessionSourcesProvider: () -> List<AgentSessionSource>,
  projectEntriesProvider: suspend () -> List<ProjectEntry>,
  warmState: SessionWarmState = InMemorySessionWarmState(),
  uiPreferencesState: AgentSessionUiPreferencesStateService = AgentSessionUiPreferencesStateService(),
  archiveChatCleanup: suspend (projectPath: String, threadIdentity: String, subAgentId: String?) -> Unit = { _, _, _ -> },
  archiveBackgroundTaskRunner: AgentSessionArchiveBackgroundTaskRunner = AgentSessionArchiveBackgroundTaskRunner { _, _, block -> block() },
  chatOpenExecutor: AgentSessionChatOpenExecutor? = null,
  openPendingCodexTabsProvider: suspend () -> Map<String, List<AgentChatPendingTabSnapshot>> =
    ::collectOpenPendingCodexTabsByPath,
  openConcreteChatThreadIdentitiesByPathProvider: suspend () -> Map<String, Set<String>> =
    ::collectOpenConcreteAgentChatThreadIdentitiesByPath,
  openAgentChatPendingTabsBinder: suspend (
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = ::rebindOpenPendingCodexTabs,
  openPendingAgentChatTabsProvider: suspend (AgentSessionProvider) -> Map<String, List<AgentChatPendingTabSnapshot>> = { provider ->
    if (provider == AgentSessionProvider.from("codex")) openPendingCodexTabsProvider() else collectOpenPendingAgentChatTabsByPath(provider)
  },
  openAgentChatPendingTabsBinderWithProvider: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = { _, requestsByPath -> openAgentChatPendingTabsBinder(requestsByPath) },
  archivedSessionsRefreshIfLoaded: () -> Unit = {},
  toolWindowVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
  currentTimeMillis: () -> Long = System::currentTimeMillis,
  loadingDelayMs: Long = 0L,
  visibleCostHydrationDelayMs: Long = 750L,
  workingThreadCostCacheTtlMs: Long = 60_000L,
  action: suspend (AgentSessionStateSyncTestFacade, AgentSessionArchiveService) -> Unit,
) {
  withServiceAndArchiveAndLaunch(
    sessionSourcesProvider = sessionSourcesProvider,
    projectEntriesProvider = projectEntriesProvider,
    warmState = warmState,
    uiPreferencesState = uiPreferencesState,
    archiveChatCleanup = archiveChatCleanup,
    archiveBackgroundTaskRunner = archiveBackgroundTaskRunner,
    chatOpenExecutor = chatOpenExecutor,
    openPendingCodexTabsProvider = openPendingCodexTabsProvider,
    openConcreteChatThreadIdentitiesByPathProvider = openConcreteChatThreadIdentitiesByPathProvider,
    openAgentChatPendingTabsBinder = openAgentChatPendingTabsBinder,
    openPendingAgentChatTabsProvider = openPendingAgentChatTabsProvider,
    openAgentChatPendingTabsBinderWithProvider = openAgentChatPendingTabsBinderWithProvider,
    archivedSessionsRefreshIfLoaded = archivedSessionsRefreshIfLoaded,
    toolWindowVisibleFlow = toolWindowVisibleFlow,
    currentTimeMillis = currentTimeMillis,
    loadingDelayMs = loadingDelayMs,
    visibleCostHydrationDelayMs = visibleCostHydrationDelayMs,
    workingThreadCostCacheTtlMs = workingThreadCostCacheTtlMs,
  ) { service, archiveService, _ ->
    action(service, archiveService)
  }
}

internal suspend fun withServiceAndArchiveAndLaunch(
  sessionSourcesProvider: () -> List<AgentSessionSource>,
  projectEntriesProvider: suspend () -> List<ProjectEntry>,
  warmState: SessionWarmState = InMemorySessionWarmState(),
  uiPreferencesState: AgentSessionUiPreferencesStateService = AgentSessionUiPreferencesStateService(),
  archiveChatCleanup: suspend (projectPath: String, threadIdentity: String, subAgentId: String?) -> Unit = { _, _, _ -> },
  archiveBackgroundTaskRunner: AgentSessionArchiveBackgroundTaskRunner = AgentSessionArchiveBackgroundTaskRunner { _, _, block -> block() },
  chatOpenExecutor: AgentSessionChatOpenExecutor? = null,
  openPendingCodexTabsProvider: suspend () -> Map<String, List<AgentChatPendingTabSnapshot>> =
    ::collectOpenPendingCodexTabsByPath,
  openConcreteChatThreadIdentitiesByPathProvider: suspend () -> Map<String, Set<String>> =
    ::collectOpenConcreteAgentChatThreadIdentitiesByPath,
  openAgentChatPendingTabsBinder: suspend (
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = ::rebindOpenPendingCodexTabs,
  openPendingAgentChatTabsProvider: suspend (AgentSessionProvider) -> Map<String, List<AgentChatPendingTabSnapshot>> = { provider ->
    if (provider == AgentSessionProvider.from("codex")) openPendingCodexTabsProvider() else collectOpenPendingAgentChatTabsByPath(provider)
  },
  openAgentChatPendingTabsBinderWithProvider: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = { _, requestsByPath -> openAgentChatPendingTabsBinder(requestsByPath) },
  archivedSessionsRefreshIfLoaded: () -> Unit = {},
  toolWindowVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
  currentTimeMillis: () -> Long = System::currentTimeMillis,
  loadingDelayMs: Long = 0L,
  visibleCostHydrationDelayMs: Long = 750L,
  workingThreadCostCacheTtlMs: Long = 60_000L,
  branchMismatchConfirmation: suspend (Project?, String, String) -> Boolean = { _, _, _ ->
    error("Unexpected branch mismatch confirmation")
  },
  action: suspend (AgentSessionStateSyncTestFacade, AgentSessionArchiveService, AgentSessionLaunchService) -> Unit,
) {
  val job = SupervisorJob()

  @Suppress("RAW_SCOPE_CREATION")
  val scope = CoroutineScope(job + Dispatchers.Default)
  var previousOpenInDedicatedFrame: Boolean? = null
  try {
    previousOpenInDedicatedFrame = AgentChatOpenModeSettings.openInDedicatedFrame()
    AgentChatOpenModeSettings.setOpenInDedicatedFrame(true)
    val stateStore = AgentSessionsStateStore()
    val archiveTransitionSuppressions = AgentSessionArchiveTransitionSuppressions()
    val contentRepository = AgentSessionContentRepository(
      stateStore = stateStore,
      warmState = warmState,
    )
    val syncService = AgentSessionRefreshService(
      serviceScope = scope,
      sessionSourcesProvider = sessionSourcesProvider,
      projectEntriesProvider = projectEntriesProvider,
      stateStore = stateStore,
      warmState = warmState,
      scheduleVfsRefresh = { _ -> },
      openAgentChatSnapshotProvider = {
        buildOpenChatRefreshSnapshot(
          pendingTabsByProvider = mapOf(
            AgentSessionProvider.from("codex") to openPendingAgentChatTabsProvider(AgentSessionProvider.from("codex")),
            AgentSessionProvider.from("claude") to openPendingAgentChatTabsProvider(AgentSessionProvider.from("claude")),
          ),
          concreteThreadIdentitiesByPath = openConcreteChatThreadIdentitiesByPathProvider(),
        )
      },
      openAgentChatPendingTabsBinder = openAgentChatPendingTabsBinderWithProvider,
      providerDescriptorProvider = { provider -> testIntegrationProviderDescriptor(provider) },
      toolWindowVisibleFlow = toolWindowVisibleFlow,
      currentTimeMillis = currentTimeMillis,
      archiveTransitionSuppressions = archiveTransitionSuppressions,
      loadingDelayMs = loadingDelayMs,
      visibleCostHydrationDelayMs = visibleCostHydrationDelayMs,
      workingThreadCostCacheTtlMs = workingThreadCostCacheTtlMs,
      subscribeToProjectLifecycle = false,
    )
    val service = AgentSessionStateSyncTestFacade(
      stateStore = stateStore,
      syncService = syncService,
    )
    val launchProfileResolver = AgentSessionLaunchProfileResolverImpl(uiPreferencesState)
    val launchService = if (chatOpenExecutor == null) {
      AgentSessionLaunchService(
        serviceScope = scope,
        stateStore = stateStore,
        syncService = syncService,
        uiPreferencesState = uiPreferencesState,
        launchProfileResolver = launchProfileResolver,
        archiveTransitionSuppressions = archiveTransitionSuppressions,
        openPendingAgentChatTabsProvider = openPendingAgentChatTabsProvider,
        openAgentChatPendingTabsBinder = openAgentChatPendingTabsBinderWithProvider,
        archivedSessionsRefreshIfLoaded = archivedSessionsRefreshIfLoaded,
        branchMismatchConfirmation = branchMismatchConfirmation,
      )
    }
    else {
      AgentSessionLaunchService(
        serviceScope = scope,
        stateStore = stateStore,
        syncService = syncService,
        uiPreferencesState = uiPreferencesState,
        launchProfileResolver = launchProfileResolver,
        chatOpenExecutor = chatOpenExecutor,
        archiveTransitionSuppressions = archiveTransitionSuppressions,
        openPendingAgentChatTabsProvider = openPendingAgentChatTabsProvider,
        openAgentChatPendingTabsBinder = openAgentChatPendingTabsBinderWithProvider,
        archivedSessionsRefreshIfLoaded = archivedSessionsRefreshIfLoaded,
        branchMismatchConfirmation = branchMismatchConfirmation,
      )
    }
    val archiveService = AgentSessionArchiveService(
      serviceScope = scope,
      syncService = syncService,
      contentRepository = contentRepository,
      archiveChatCleanup = archiveChatCleanup,
      backgroundTaskRunner = archiveBackgroundTaskRunner,
      archiveTransitionSuppressions = archiveTransitionSuppressions,
    )
    action(service, archiveService, launchService)
  }
  finally {
    previousOpenInDedicatedFrame?.let { AgentChatOpenModeSettings.setOpenInDedicatedFrame(it) }
    job.cancelAndJoin()
  }
}

fun openTestProjectEntry(
  path: String,
  name: String,
  worktrees: List<TestWorktreeCatalogEntry> = emptyList(),
  branch: String? = null,
  projectDirectory: String? = null,
): TestProjectCatalogEntry {
  return TestProjectCatalogEntry(
    path = path,
    name = name,
    branch = branch,
    worktrees = worktrees,
    isOpen = true,
    projectDirectory = projectDirectory,
  )
}

internal fun openProjectEntry(
  path: String,
  name: String,
  worktrees: List<WorktreeEntry> = emptyList(),
  branch: String? = null,
  projectDirectory: String? = null,
): ProjectEntry {
  val resolvedProjectDirectory = projectDirectory ?: path
  return ProjectEntry(
    path = path,
    projectDirectory = resolvedProjectDirectory,
    name = name,
    project = openProjectProxy(name = name, basePath = resolvedProjectDirectory),
    branch = branch,
    worktreeEntries = worktrees,
  )
}

internal fun closedProjectEntry(
  path: String,
  name: String,
  worktrees: List<WorktreeEntry> = emptyList(),
  branch: String? = null,
  projectDirectory: String? = null,
): ProjectEntry {
  return ProjectEntry(
    path = path,
    projectDirectory = projectDirectory ?: path,
    name = name,
    project = null,
    branch = branch,
    worktreeEntries = worktrees,
  )
}

internal fun openProjectProxy(
  name: String,
  basePath: String? = null,
  services: Map<Class<*>, Any> = emptyMap(),
): Project {
  val handler = InvocationHandler { proxy, method, args ->
    when (method.name) {
      "getName" -> name
      "getBasePath" -> basePath
      "getService", "getServiceAsync" -> services[args?.firstOrNull() as? Class<*>]
      "isOpen" -> true
      "isDisposed" -> false
      "toString" -> "MockProject($name)"
      "hashCode" -> System.identityHashCode(proxy)
      "equals" -> proxy === args?.firstOrNull()
      else -> defaultValue(method.returnType)
    }
  }
  return Proxy.newProxyInstance(
    Project::class.java.classLoader,
    arrayOf(Project::class.java, ComponentManagerEx::class.java),
    handler,
  ) as Project
}

private fun defaultValue(returnType: Class<*>): Any? {
  return when {
    !returnType.isPrimitive -> null
    returnType == Boolean::class.javaPrimitiveType -> false
    returnType == Int::class.javaPrimitiveType -> 0
    returnType == Long::class.javaPrimitiveType -> 0L
    returnType == Short::class.javaPrimitiveType -> 0.toShort()
    returnType == Byte::class.javaPrimitiveType -> 0.toByte()
    returnType == Float::class.javaPrimitiveType -> 0f
    returnType == Double::class.javaPrimitiveType -> 0.0
    returnType == Char::class.javaPrimitiveType -> '\u0000'
    else -> null
  }
}

private suspend fun collectOpenPendingCodexTabsByPath(): Map<String, List<AgentChatPendingTabSnapshot>> {
  return collectOpenPendingAgentChatTabsByPath(AgentSessionProvider.from("codex"))
}

private suspend fun rebindOpenPendingCodexTabs(
  requestsByProjectPath: Map<String, List<AgentChatPendingTabRebindRequest>>,
): AgentChatPendingTabRebindReport {
  return rebindOpenPendingAgentChatTabs(
    provider = AgentSessionProvider.from("codex"),
    requestsByProjectPath = requestsByProjectPath,
  )
}

suspend fun waitForCondition(timeoutMs: Long = 5_000, condition: () -> Boolean) {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (System.currentTimeMillis() < deadline) {
    if (condition()) {
      return
    }
    delay(20.milliseconds)
  }
  throw AssertionError("Condition was not satisfied within ${timeoutMs}ms")
}

private val TEST_INTEGRATION_PROVIDER_DESCRIPTORS: Map<AgentSessionProvider, TestAgentSessionProviderDescriptor> = listOf(
  TestAgentSessionProviderDescriptor(
    provider = AgentSessionProvider.from("codex"),
    supportedModes = emptySet(),
    cliAvailable = true,
    supportsPendingEditorTabRebind = true,
    supportsNewThreadRebind = true,
    emitsScopedRefreshSignals = true,
    refreshPathAfterCreateNewSession = true,
  ),
  TestAgentSessionProviderDescriptor(
    provider = AgentSessionProvider.from("claude"),
    supportedModes = emptySet(),
    cliAvailable = true,
    supportsPendingEditorTabRebind = true,
    emitsScopedRefreshSignals = true,
    refreshPathAfterCreateNewSession = true,
  ),
).associateBy { it.provider }

private fun testIntegrationProviderDescriptor(provider: AgentSessionProvider): TestAgentSessionProviderDescriptor? {
  return TEST_INTEGRATION_PROVIDER_DESCRIPTORS[provider]
}

private fun TestProjectCatalogEntry.toProjectEntry(): ProjectEntry {
  val resolvedProjectDirectory = projectDirectory ?: path
  return ProjectEntry(
    path = path,
    projectDirectory = resolvedProjectDirectory,
    name = name,
    project = if (isOpen) openProjectProxy(name = name, basePath = resolvedProjectDirectory) else null,
    branch = branch,
    worktreeEntries = worktrees.map { it.toWorktreeEntry() },
  )
}

private fun TestWorktreeCatalogEntry.toWorktreeEntry(): WorktreeEntry {
  val resolvedProjectDirectory = projectDirectory ?: path
  return WorktreeEntry(
    path = path,
    projectDirectory = resolvedProjectDirectory,
    name = name,
    branch = branch,
    project = if (isOpen) openProjectProxy(name = name, basePath = resolvedProjectDirectory) else null,
  )
}
