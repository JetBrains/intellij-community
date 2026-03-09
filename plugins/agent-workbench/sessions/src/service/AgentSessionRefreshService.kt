// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatTabSelectionService
import com.intellij.agent.workbench.chat.agentChatScopedRefreshSignals
import com.intellij.agent.workbench.chat.clearOpenConcreteAgentChatNewThreadRebindAnchors
import com.intellij.agent.workbench.chat.collectOpenConcreteAgentChatTabsAwaitingNewThreadRebindByPath
import com.intellij.agent.workbench.chat.collectOpenConcreteAgentChatThreadIdentitiesByPath
import com.intellij.agent.workbench.chat.collectOpenPendingAgentChatTabsByPath
import com.intellij.agent.workbench.chat.rebindOpenConcreteAgentChatTabs
import com.intellij.agent.workbench.chat.rebindOpenPendingAgentChatTabs
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.frame.AGENT_SESSIONS_TOOL_WINDOW_ID
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.model.ProjectEntry
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.state.AgentSessionsTreeUiStateService
import com.intellij.agent.workbench.sessions.state.SessionsTreeUiState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LOG = logger<AgentSessionRefreshService>()

@Service(Service.Level.APP)
internal class AgentSessionRefreshService(
  private val serviceScope: CoroutineScope,
  private val sessionSourcesProvider: () -> List<AgentSessionSource>,
  private val projectEntriesProvider: suspend () -> List<ProjectEntry>,
  private val stateStore: AgentSessionsStateStore,
  private val warmState: SessionWarmState,
    private val openPendingCodexTabsProvider: suspend (AgentSessionProvider) -> Map<String, List<AgentChatPendingCodexTabSnapshot>> =
    ::collectOpenPendingAgentChatTabsByPath,
    private val openConcreteCodexTabsAwaitingNewThreadRebindProvider: suspend (AgentSessionProvider) -> Map<String, List<AgentChatConcreteCodexTabSnapshot>> =
    ::collectOpenConcreteAgentChatTabsAwaitingNewThreadRebindByPath,
    private val openConcreteChatThreadIdentitiesByPathProvider: suspend () -> Map<String, Set<String>> =
    ::collectOpenConcreteAgentChatThreadIdentitiesByPath,
    private val openAgentChatPendingTabsBinder: (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingCodexTabRebindRequest>>,
  ) -> AgentChatPendingCodexTabRebindReport = ::rebindOpenPendingAgentChatTabs,
    private val openAgentChatConcreteTabsBinder: (
    AgentSessionProvider,
    Map<String, List<AgentChatConcreteCodexTabRebindRequest>>,
  ) -> AgentChatConcreteCodexTabRebindReport = ::rebindOpenConcreteAgentChatTabs,
    private val clearOpenConcreteCodexTabAnchors: (
    AgentSessionProvider,
    Map<String, List<AgentChatConcreteCodexTabSnapshot>>,
  ) -> Int = ::clearOpenConcreteAgentChatNewThreadRebindAnchors,
    private val codexScopedRefreshSignalsProvider: (AgentSessionProvider) -> kotlinx.coroutines.flow.Flow<Set<String>> = { provider ->
      agentChatScopedRefreshSignals(provider)
    },
    subscribeToProjectLifecycle: Boolean,
) {
  @Suppress("unused")
  constructor(serviceScope: CoroutineScope) : this(
    serviceScope = serviceScope,
    sessionSourcesProvider = AgentSessionProviderBridges::sessionSources,
    projectEntriesProvider = AgentSessionProjectCatalog()::collectProjects,
    stateStore = service<AgentSessionsStateStore>(),
    treeUiState = service<AgentSessionsTreeUiStateService>(),
    subscribeToProjectLifecycle = true,
  )

  private val loadingCoordinator = AgentSessionRefreshCoordinator(
    serviceScope = serviceScope,
    sessionSourcesProvider = sessionSourcesProvider,
    projectEntriesProvider = projectEntriesProvider,
    treeUiState = treeUiState,
    stateStore = stateStore,
    isRefreshGateActive = ::isSourceRefreshGateActive,
    codexScopedRefreshSignalsProvider = codexScopedRefreshSignalsProvider,
    openPendingCodexTabsProvider = openPendingCodexTabsProvider,
    openConcreteChatThreadIdentitiesByPathProvider = openConcreteChatThreadIdentitiesByPathProvider,
    openAgentChatPendingTabsBinder = openAgentChatPendingTabsBinder,
  )

  init {
    loadingCoordinator.observeSessionSourceUpdates()

    if (subscribeToProjectLifecycle) {
      ApplicationManager.getApplication().messageBus.connect(serviceScope)
        .subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
          @Deprecated("Deprecated in Java")
          @Suppress("removal")
          override fun projectOpened(project: Project) {
            refreshCatalogAndLoadNewlyOpened()
          }

          override fun projectClosed(project: Project) {
            refreshCatalogAndLoadNewlyOpened()
          }
        })
    }
  }

  private suspend fun isSourceRefreshGateActive(): Boolean = withContext(Dispatchers.EDT) {
    val stateSnapshot = stateStore.snapshot()
    val hasLoadedPaths = stateSnapshot.projects.any { project ->
      project.hasLoaded || project.worktrees.any { it.hasLoaded }
    }

    val openProjects = ProjectManager.getInstance().openProjects
    if (openProjects.isEmpty()) {
      val decision = stateSnapshot.projects.any { project ->
        project.isOpen || project.hasLoaded || project.worktrees.any { it.isOpen || it.hasLoaded }
      }
      LOG.debug {
        "Source refresh gate decision=$decision (openProjects=0, stateProjects=${stateSnapshot.projects.size}, hasLoadedPaths=$hasLoadedPaths)"
      }
      return@withContext decision
    }

    data class ProjectRefreshSignal(
      val name: String,
      val dedicated: Boolean,
      val sessionsVisible: Boolean,
      val chatActive: Boolean,
    )

    val signals = openProjects.map { project ->
      ProjectRefreshSignal(
        name = project.name,
        dedicated = AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProject(project),
        sessionsVisible = isSessionsToolWindowVisible(project),
        chatActive = isAgentChatActive(project),
      )
    }

    val uiSignalActive = signals.any { signal ->
      signal.sessionsVisible || signal.chatActive
    }
    val decision = uiSignalActive || hasLoadedPaths

    LOG.debug {
      val signalText = signals.joinToString(separator = ";") { signal ->
        "${signal.name}[dedicated=${signal.dedicated},sessionsVisible=${signal.sessionsVisible},chatActive=${signal.chatActive}]"
      }
      "Source refresh gate decision=$decision (openProjects=${openProjects.size}, uiSignalActive=$uiSignalActive, hasLoadedPaths=$hasLoadedPaths, signals=$signalText)"
    }

    decision
  }

  fun refresh() {
    loadingCoordinator.refresh()
  }

  fun refreshCatalogAndLoadNewlyOpened() {
    loadingCoordinator.refreshCatalogAndLoadNewlyOpened()
  }

  fun refreshProviderForPath(path: String, provider: AgentSessionProvider) {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    loadingCoordinator.refreshProviderScope(provider = provider, scopedPaths = setOf(normalizedPath))
  }

  fun appendProviderUnavailableWarning(path: String, provider: AgentSessionProvider) {
    loadingCoordinator.appendProviderUnavailableWarning(path = path, provider = provider)
  }

  fun suppressArchivedThread(path: String, provider: AgentSessionProvider, threadId: String) {
    loadingCoordinator.suppressArchivedThread(path = path, provider = provider, threadId = threadId)
  }

  fun unsuppressArchivedThread(path: String, provider: AgentSessionProvider, threadId: String) {
    loadingCoordinator.unsuppressArchivedThread(path = path, provider = provider, threadId = threadId)
  }

  fun loadProjectThreadsOnDemand(path: String) {
    loadingCoordinator.loadProjectThreadsOnDemand(path)
  }

  fun loadWorktreeThreadsOnDemand(projectPath: String, worktreePath: String) {
    loadingCoordinator.loadWorktreeThreadsOnDemand(projectPath, worktreePath)
  }
}

private fun isSessionsToolWindowVisible(project: Project): Boolean {
  return ToolWindowManager.getInstance(project)
    .getToolWindow(AGENT_SESSIONS_TOOL_WINDOW_ID)
    ?.isVisible == true
}

private fun isAgentChatActive(project: Project): Boolean {
  return runCatching {
    val selectionService = project.service<AgentChatTabSelectionService>()
    selectionService.selectedChatTab.value != null || selectionService.hasOpenChatTabs()
  }.getOrDefault(false)
}
