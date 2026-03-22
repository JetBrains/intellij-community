// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatConcreteTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatConcreteTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatConcreteTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatOpenTabsRefreshSnapshot
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatTabSelectionService
import com.intellij.agent.workbench.chat.agentChatScopedRefreshSignals
import com.intellij.agent.workbench.chat.clearOpenConcreteAgentChatNewThreadRebindAnchors
import com.intellij.agent.workbench.chat.collectOpenAgentChatRefreshSnapshot
import com.intellij.agent.workbench.chat.rebindOpenConcreteAgentChatTabs
import com.intellij.agent.workbench.chat.rebindOpenPendingAgentChatTabs
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LOG = logger<AgentSessionRefreshService>()

@Service(Service.Level.APP)
internal class AgentSessionRefreshService(
  private val serviceScope: CoroutineScope,
  private val sessionSourcesProvider: () -> List<AgentSessionSource>,
  private val projectEntriesProvider: suspend () -> List<ProjectEntry>,
  private val stateStore: AgentSessionsStateStore,
  private val warmState: SessionWarmState,
  private val openAgentChatSnapshotProvider: suspend () -> AgentChatOpenTabsRefreshSnapshot =
    ::collectOpenAgentChatRefreshSnapshot,
  private val openAgentChatPendingTabsBinder: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport = ::rebindOpenPendingAgentChatTabs,
  private val openAgentChatConcreteTabsBinder: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatConcreteTabRebindRequest>>,
  ) -> AgentChatConcreteTabRebindReport = ::rebindOpenConcreteAgentChatTabs,
  private val clearOpenConcreteNewThreadRebindAnchors: (
      AgentSessionProvider,
      Map<String, List<AgentChatConcreteTabSnapshot>>,
  ) -> Int = ::clearOpenConcreteAgentChatNewThreadRebindAnchors,
  private val scopedRefreshSignalsProvider: (AgentSessionProvider) -> kotlinx.coroutines.flow.Flow<Set<String>> = { provider ->
    agentChatScopedRefreshSignals(provider)
  },
  subscribeToProjectLifecycle: Boolean,
) {
  @Suppress("unused")
  constructor(serviceScope: CoroutineScope) : this(
    serviceScope = serviceScope,
    sessionSourcesProvider = AgentSessionProviders::sessionSources,
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
    openAgentChatSnapshotProvider = openAgentChatSnapshotProvider,
    scopedRefreshSignalsProvider = scopedRefreshSignalsProvider,
    openAgentChatPendingTabsBinder = openAgentChatPendingTabsBinder,
    openAgentChatConcreteTabsBinder = openAgentChatConcreteTabsBinder,
    clearOpenConcreteNewThreadRebindAnchors = clearOpenConcreteNewThreadRebindAnchors,
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

  fun rebindPendingTabsInBackground(
    provider: AgentSessionProvider,
    requestsByProjectPath: Map<String, List<AgentChatPendingTabRebindRequest>>,
  ): Job {
    return serviceScope.launch(Dispatchers.IO) {
      openAgentChatPendingTabsBinder(provider, requestsByProjectPath)
    }
  }

  internal fun prepareThreadForOpen(path: String, provider: AgentSessionProvider, threadId: String, updatedAt: Long) {
    contentRepository.markThreadAsRead(path = path, provider = provider, threadId = threadId, updatedAt = updatedAt)
    val source = sessionSourcesProvider().firstOrNull { it.provider == provider } ?: return
    source.setActiveThreadId(threadId)
    source.markThreadAsRead(threadId, updatedAt)
  }

  fun markThreadAsRead(path: String, provider: AgentSessionProvider, threadId: String, updatedAt: Long) {
    contentRepository.markThreadAsRead(path = path, provider = provider, threadId = threadId, updatedAt = updatedAt)
    val source = sessionSourcesProvider().firstOrNull { it.provider == provider } ?: return
    source.markThreadAsRead(threadId, updatedAt)
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
