// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatConcreteTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatOpenTabsRefreshSnapshot
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatPendingTabSnapshot
import com.intellij.agent.workbench.chat.collectOpenConcreteAgentChatThreadIdentitiesByPath
import com.intellij.agent.workbench.chat.collectOpenPendingAgentChatTabsByPath
import com.intellij.agent.workbench.chat.collectOpenPendingCodexTabsByPath
import com.intellij.agent.workbench.chat.rebindOpenPendingCodexTabs
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.session.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceRefreshRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceRefreshResult
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadActivityUpdate
import com.intellij.agent.workbench.sessions.frame.AgentChatOpenModeSettings
import com.intellij.agent.workbench.sessions.frame.OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.ProjectEntry
import com.intellij.agent.workbench.sessions.model.WorktreeEntry
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveBackgroundTaskRunner
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveService
import com.intellij.agent.workbench.sessions.service.AgentSessionChatOpenExecutor
import com.intellij.agent.workbench.sessions.service.AgentSessionContentRepository
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.service.AgentSessionRefreshService
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.state.InMemorySessionWarmState
import com.intellij.agent.workbench.sessions.state.SessionWarmState
import com.intellij.openapi.options.advanced.AdvancedSettingBean
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CoroutineScope
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

internal fun registerDedicatedFrameSettingForTest(disposable: com.intellij.openapi.Disposable) {
  if (AdvancedSettingBean.EP_NAME.extensionList.none { it.id == OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID }) {
    AdvancedSettingBean.EP_NAME.point.registerExtension(
      AdvancedSettingBean().apply {
        id = OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID
        defaultValue = "true"
        groupKey = "agent.workbench.tests"
      },
      disposable,
    )
  }
}

data class TestProjectCatalogEntry(
  @JvmField val path: String,
  @JvmField val name: String,
  @JvmField val worktrees: List<TestWorktreeCatalogEntry> = emptyList(),
  @JvmField val branch: String? = null,
  @JvmField val isOpen: Boolean = true,
)

data class TestWorktreeCatalogEntry(
  @JvmField val path: String,
  @JvmField val name: String,
  @JvmField val branch: String?,
  @JvmField val isOpen: Boolean = false,
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
  override val supportsArchivedThreads: Boolean = false,
  override val supportsUpdates: Boolean = false,
  override val updateEvents: Flow<AgentSessionSourceUpdateEvent> = emptyFlow(),
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
) : AgentSessionSource {
  override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> {
    return listFromOpenProject(path, project)
  }

  override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> {
    return listFromClosedProject(path)
  }

  override suspend fun listArchivedThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> {
    return listArchivedFromOpenProject(path, project)
  }

  override suspend fun listArchivedThreadsFromClosedProject(path: String): List<AgentSessionThread> {
    return listArchivedFromClosedProject(path)
  }

  override suspend fun prefetchThreads(paths: List<String>): Map<String, List<AgentSessionThread>> {
    return prefetch(paths)
  }

  override suspend fun refreshThreads(request: AgentSessionSourceRefreshRequest): AgentSessionSourceRefreshResult {
    return refreshThreadsProvider?.invoke(request) ?: super.refreshThreads(request)
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

fun threadsChangedEvent(
  scopedPaths: Set<String>? = null,
  threadIds: Set<String>? = null,
  activityUpdatesByThreadId: Map<String, AgentSessionThreadActivityUpdate> = emptyMap(),
  mayHaveChangedProjectFiles: Boolean = false,
  changedProjectFilePaths: Set<String>? = null,
): AgentSessionSourceUpdateEvent {
  return AgentSessionSourceUpdateEvent(
    type = AgentSessionSourceUpdate.THREADS_CHANGED,
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
  return AgentSessionSourceUpdateEvent(
    type = AgentSessionSourceUpdate.HINTS_CHANGED,
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
    if (provider == AgentSessionProvider.CODEX) openPendingCodexTabsProvider() else collectOpenPendingAgentChatTabsByPath(provider)
  },
  openAgentChatPendingTabsBinderWithProvider: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = { _, requestsByPath -> openAgentChatPendingTabsBinder(requestsByPath) },
  archivedSessionsRefreshIfLoaded: () -> Unit = {},
  toolWindowVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
  currentTimeMillis: () -> Long = System::currentTimeMillis,
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
    if (provider == AgentSessionProvider.CODEX) openPendingCodexTabsProvider() else collectOpenPendingAgentChatTabsByPath(provider)
  },
  openAgentChatPendingTabsBinderWithProvider: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = { _, requestsByPath -> openAgentChatPendingTabsBinder(requestsByPath) },
  archivedSessionsRefreshIfLoaded: () -> Unit = {},
  toolWindowVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
  currentTimeMillis: () -> Long = System::currentTimeMillis,
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
    if (provider == AgentSessionProvider.CODEX) openPendingCodexTabsProvider() else collectOpenPendingAgentChatTabsByPath(provider)
  },
  openAgentChatPendingTabsBinderWithProvider: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = { _, requestsByPath -> openAgentChatPendingTabsBinder(requestsByPath) },
  archivedSessionsRefreshIfLoaded: () -> Unit = {},
  toolWindowVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
  currentTimeMillis: () -> Long = System::currentTimeMillis,
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
    if (provider == AgentSessionProvider.CODEX) openPendingCodexTabsProvider() else collectOpenPendingAgentChatTabsByPath(provider)
  },
  openAgentChatPendingTabsBinderWithProvider: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = { _, requestsByPath -> openAgentChatPendingTabsBinder(requestsByPath) },
  archivedSessionsRefreshIfLoaded: () -> Unit = {},
  toolWindowVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
  currentTimeMillis: () -> Long = System::currentTimeMillis,
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
    if (provider == AgentSessionProvider.CODEX) openPendingCodexTabsProvider() else collectOpenPendingAgentChatTabsByPath(provider)
  },
  openAgentChatPendingTabsBinderWithProvider: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = { _, requestsByPath -> openAgentChatPendingTabsBinder(requestsByPath) },
  archivedSessionsRefreshIfLoaded: () -> Unit = {},
  toolWindowVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
  currentTimeMillis: () -> Long = System::currentTimeMillis,
  branchMismatchConfirmation: suspend (Project?, String, String) -> Boolean = { _, _, _ ->
    error("Unexpected branch mismatch confirmation")
  },
  action: suspend (AgentSessionStateSyncTestFacade, AgentSessionArchiveService, AgentSessionLaunchService) -> Unit,
) {
  val job = SupervisorJob()

  @Suppress("RAW_SCOPE_CREATION")
  val scope = CoroutineScope(job + Dispatchers.Default)
  val settingDisposable = Disposer.newDisposable()
  var previousOpenInDedicatedFrame: Boolean? = null
  try {
    registerDedicatedFrameSettingForTest(settingDisposable)
    previousOpenInDedicatedFrame = AgentChatOpenModeSettings.openInDedicatedFrame()
    AgentChatOpenModeSettings.setOpenInDedicatedFrame(true)
    val stateStore = AgentSessionsStateStore()
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
            AgentSessionProvider.CODEX to openPendingAgentChatTabsProvider(AgentSessionProvider.CODEX),
            AgentSessionProvider.CLAUDE to openPendingAgentChatTabsProvider(AgentSessionProvider.CLAUDE),
          ),
          concreteThreadIdentitiesByPath = openConcreteChatThreadIdentitiesByPathProvider(),
        )
      },
      openAgentChatPendingTabsBinder = openAgentChatPendingTabsBinderWithProvider,
      providerDescriptorProvider = { provider -> testIntegrationProviderDescriptor(provider) },
      toolWindowVisibleFlow = toolWindowVisibleFlow,
      currentTimeMillis = currentTimeMillis,
      subscribeToProjectLifecycle = false,
    )
    val service = AgentSessionStateSyncTestFacade(
      stateStore = stateStore,
      syncService = syncService,
    )
    val launchService = if (chatOpenExecutor == null) {
      AgentSessionLaunchService(
        serviceScope = scope,
        stateStore = stateStore,
        syncService = syncService,
        uiPreferencesState = uiPreferencesState,
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
        chatOpenExecutor = chatOpenExecutor,
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
    )
    action(service, archiveService, launchService)
  }
  finally {
    previousOpenInDedicatedFrame?.let { AgentChatOpenModeSettings.setOpenInDedicatedFrame(it) }
    Disposer.dispose(settingDisposable)
    job.cancelAndJoin()
  }
}

fun openTestProjectEntry(
  path: String,
  name: String,
  worktrees: List<TestWorktreeCatalogEntry> = emptyList(),
  branch: String? = null,
): TestProjectCatalogEntry {
  return TestProjectCatalogEntry(
    path = path,
    name = name,
    branch = branch,
    worktrees = worktrees,
    isOpen = true,
  )
}

internal fun openProjectEntry(
  path: String,
  name: String,
  worktrees: List<WorktreeEntry> = emptyList(),
  branch: String? = null,
): ProjectEntry {
  return ProjectEntry(
    path = path,
    name = name,
    project = openProjectProxy(name),
    branch = branch,
    worktreeEntries = worktrees,
  )
}

internal fun closedProjectEntry(
  path: String,
  name: String,
  worktrees: List<WorktreeEntry> = emptyList(),
  branch: String? = null,
): ProjectEntry {
  return ProjectEntry(
    path = path,
    name = name,
    project = null,
    branch = branch,
    worktreeEntries = worktrees,
  )
}

private fun openProjectProxy(name: String): Project {
  val handler = InvocationHandler { proxy, method, args ->
    when (method.name) {
      "getName" -> name
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
    arrayOf(Project::class.java),
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
    provider = AgentSessionProvider.CODEX,
    supportedModes = emptySet(),
    cliAvailable = true,
    supportsPendingEditorTabRebind = true,
    supportsNewThreadRebind = true,
    emitsScopedRefreshSignals = true,
    refreshPathAfterCreateNewSession = true,
  ),
  TestAgentSessionProviderDescriptor(
    provider = AgentSessionProvider.CLAUDE,
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
  return ProjectEntry(
    path = path,
    name = name,
    project = if (isOpen) openProjectProxy(name) else null,
    branch = branch,
    worktreeEntries = worktrees.map { it.toWorktreeEntry() },
  )
}

private fun TestWorktreeCatalogEntry.toWorktreeEntry(): WorktreeEntry {
  return WorktreeEntry(
    path = path,
    name = name,
    branch = branch,
    project = if (isOpen) openProjectProxy(name) else null,
  )
}
