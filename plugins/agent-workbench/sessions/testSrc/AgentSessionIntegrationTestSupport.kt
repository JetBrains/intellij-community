// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabSnapshot
import com.intellij.agent.workbench.chat.collectOpenConcreteAgentChatThreadIdentitiesByPath
import com.intellij.agent.workbench.chat.collectOpenPendingCodexTabsByPath
import com.intellij.agent.workbench.chat.rebindOpenPendingCodexTabs
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.model.ProjectEntry
import com.intellij.agent.workbench.sessions.model.WorktreeEntry
import com.intellij.agent.workbench.sessions.service.AgentSessionArchiveService
import com.intellij.agent.workbench.sessions.service.AgentSessionChatOpenExecutor
import com.intellij.agent.workbench.sessions.service.AgentSessionContentRepository
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.service.AgentSessionRefreshService
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.state.InMemorySessionWarmState
import com.intellij.agent.workbench.sessions.state.SessionWarmState
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.time.Duration.Companion.milliseconds

internal const val PROJECT_PATH = "/work/project-a"
internal const val WORKTREE_PATH = "/work/project-feature"

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

  fun markThreadAsRead(path: String, provider: AgentSessionProvider, threadId: String, updatedAt: Long) {
    syncService.markThreadAsRead(path = path, provider = provider, threadId = threadId, updatedAt = updatedAt)
  }

  fun showMoreThreads(path: String) {
    stateStore.showMoreThreads(path)
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

internal class ScriptedSessionSource(
  override val provider: AgentSessionProvider,
  override val canReportExactThreadCount: Boolean = true,
  override val supportsUpdates: Boolean = false,
  override val updates: Flow<Unit> = emptyFlow(),
  private val listFromOpenProject: suspend (path: String, project: Project) -> List<AgentSessionThread> = { _, _ -> emptyList() },
  private val listFromClosedProject: suspend (path: String) -> List<AgentSessionThread> = { _ -> emptyList() },
  private val prefetch: suspend (paths: List<String>) -> Map<String, List<AgentSessionThread>> = { emptyMap() },
  private val prefetchRefreshHintsProvider: suspend (
    paths: List<String>,
    knownThreadIdsByPath: Map<String, Set<String>>,
  ) -> Map<String, AgentSessionRefreshHints> = { _, _ -> emptyMap() },
) : AgentSessionSource {
  override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> {
    return listFromOpenProject(path, project)
  }

  override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> {
    return listFromClosedProject(path)
  }

  override suspend fun prefetchThreads(paths: List<String>): Map<String, List<AgentSessionThread>> {
    return prefetch(paths)
  }

  override suspend fun prefetchRefreshHints(
    paths: List<String>,
    knownThreadIdsByPath: Map<String, Set<String>>,
  ): Map<String, AgentSessionRefreshHints> {
    return prefetchRefreshHintsProvider(paths, knownThreadIdsByPath)
  }
}

internal fun thread(
  id: String,
  updatedAt: Long,
  provider: AgentSessionProvider,
  title: String = id,
  activity: AgentThreadActivity = AgentThreadActivity.READY,
  subAgents: List<AgentSubAgent> = emptyList(),
): AgentSessionThread {
  return AgentSessionThread(
    id = id,
    title = title,
    updatedAt = updatedAt,
    archived = false,
    provider = provider,
    activity = activity,
    subAgents = subAgents,
  )
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

internal suspend fun withService(
  sessionSourcesProvider: () -> List<AgentSessionSource>,
  projectEntriesProvider: suspend () -> List<ProjectEntry>,
  warmState: SessionWarmState = InMemorySessionWarmState(),
  uiPreferencesState: AgentSessionUiPreferencesStateService = AgentSessionUiPreferencesStateService(),
  chatOpenExecutor: AgentSessionChatOpenExecutor? = null,
  openPendingCodexTabsProvider: suspend () -> Map<String, List<AgentChatPendingCodexTabSnapshot>> =
    ::collectOpenPendingCodexTabsByPath,
  openConcreteChatThreadIdentitiesByPathProvider: suspend () -> Map<String, Set<String>> =
    ::collectOpenConcreteAgentChatThreadIdentitiesByPath,
  openAgentChatPendingTabsBinder: (
    Map<String, List<AgentChatPendingCodexTabRebindRequest>>,
  ) -> AgentChatPendingCodexTabRebindReport = ::rebindOpenPendingCodexTabs,
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
  openPendingCodexTabsProvider: suspend () -> Map<String, List<AgentChatPendingCodexTabSnapshot>> =
    ::collectOpenPendingCodexTabsByPath,
  openConcreteChatThreadIdentitiesByPathProvider: suspend () -> Map<String, Set<String>> =
    ::collectOpenConcreteAgentChatThreadIdentitiesByPath,
  openAgentChatPendingTabsBinder: (
    Map<String, List<AgentChatPendingCodexTabRebindRequest>>,
  ) -> AgentChatPendingCodexTabRebindReport = ::rebindOpenPendingCodexTabs,
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
  chatOpenExecutor: AgentSessionChatOpenExecutor? = null,
  openPendingCodexTabsProvider: suspend () -> Map<String, List<AgentChatPendingCodexTabSnapshot>> =
    ::collectOpenPendingCodexTabsByPath,
  openConcreteChatThreadIdentitiesByPathProvider: suspend () -> Map<String, Set<String>> =
    ::collectOpenConcreteAgentChatThreadIdentitiesByPath,
  openAgentChatPendingTabsBinder: (
    Map<String, List<AgentChatPendingCodexTabRebindRequest>>,
  ) -> AgentChatPendingCodexTabRebindReport = ::rebindOpenPendingCodexTabs,
  action: suspend (AgentSessionStateSyncTestFacade, AgentSessionArchiveService) -> Unit,
) {
  withServiceAndArchiveAndLaunch(
    sessionSourcesProvider = sessionSourcesProvider,
    projectEntriesProvider = projectEntriesProvider,
    warmState = warmState,
    uiPreferencesState = uiPreferencesState,
    archiveChatCleanup = archiveChatCleanup,
    chatOpenExecutor = chatOpenExecutor,
    openPendingCodexTabsProvider = openPendingCodexTabsProvider,
    openConcreteChatThreadIdentitiesByPathProvider = openConcreteChatThreadIdentitiesByPathProvider,
    openAgentChatPendingTabsBinder = openAgentChatPendingTabsBinder,
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
  chatOpenExecutor: AgentSessionChatOpenExecutor? = null,
  openPendingCodexTabsProvider: suspend () -> Map<String, List<AgentChatPendingCodexTabSnapshot>> =
    ::collectOpenPendingCodexTabsByPath,
  openConcreteChatThreadIdentitiesByPathProvider: suspend () -> Map<String, Set<String>> =
    ::collectOpenConcreteAgentChatThreadIdentitiesByPath,
  openAgentChatPendingTabsBinder: (
    Map<String, List<AgentChatPendingCodexTabRebindRequest>>,
  ) -> AgentChatPendingCodexTabRebindReport = ::rebindOpenPendingCodexTabs,
  action: suspend (AgentSessionStateSyncTestFacade, AgentSessionArchiveService, AgentSessionLaunchService) -> Unit,
) {
  val job = SupervisorJob()
  @Suppress("RAW_SCOPE_CREATION")
  val scope = CoroutineScope(job + Dispatchers.Default)
  try {
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
      openPendingCodexTabsProvider = { _ -> openPendingCodexTabsProvider() },
      openConcreteChatThreadIdentitiesByPathProvider = openConcreteChatThreadIdentitiesByPathProvider,
      openAgentChatPendingTabsBinder = { _, requestsByPath -> openAgentChatPendingTabsBinder(requestsByPath) },
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
      )
    }
    else {
      AgentSessionLaunchService(
        serviceScope = scope,
        stateStore = stateStore,
        syncService = syncService,
        uiPreferencesState = uiPreferencesState,
        chatOpenExecutor = chatOpenExecutor,
      )
    }
    val archiveService = AgentSessionArchiveService(
      serviceScope = scope,
      syncService = syncService,
      contentRepository = contentRepository,
      archiveChatCleanup = archiveChatCleanup,
    )
    action(service, archiveService, launchService)
  }
  finally {
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
