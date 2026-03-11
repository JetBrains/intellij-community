// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatTabSelectionService
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.prompt.AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptExistingThreadsSnapshot
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchResult
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLauncherBridge
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.sessions.core.prompt.getAgentPromptProjectPathContext
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.state.AgentSessionUiPreferencesStateService
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class AgentSessionPromptLauncherBridge : AgentPromptLauncherBridge {
  private val launchPromptRequest: (AgentPromptLaunchRequest) -> AgentPromptLaunchResult
  private val stateFlowProvider: () -> StateFlow<AgentSessionsState>
  private val pathStateResolver: (AgentSessionsState, String) -> AgentSessionPathState?
  private val refreshCatalogAndLoadNewlyOpened: () -> Unit
  private val refreshProviderForPath: (String, AgentSessionProvider) -> Unit
  private val preferredProviderProvider: () -> AgentSessionProvider?
  private val sourceProjectResolver: (String) -> Project?

  @Suppress("unused")
  constructor() : this(
    launchPromptRequest = { request -> service<AgentSessionLaunchService>().launchPromptRequest(request) },
    stateFlowProvider = { service<AgentSessionReadService>().stateFlow() },
    pathStateResolver = ::resolveAgentSessionPathState,
    refreshCatalogAndLoadNewlyOpened = { service<AgentSessionRefreshService>().refreshCatalogAndLoadNewlyOpened() },
    refreshProviderForPath = { path, provider -> service<AgentSessionRefreshService>().refreshProviderForPath(path = path, provider = provider) },
    preferredProviderProvider = { service<AgentSessionUiPreferencesStateService>().getLastUsedProvider() },
    sourceProjectResolver = ::findOpenSourceProjectByPath,
  )

  internal constructor(
    launchPromptRequest: (AgentPromptLaunchRequest) -> AgentPromptLaunchResult,
  ) : this(
    launchPromptRequest = launchPromptRequest,
    stateFlowProvider = {
      error("stateFlowProvider is unavailable in this test setup")
    },
    pathStateResolver = ::resolveAgentSessionPathState,
    refreshCatalogAndLoadNewlyOpened = {},
    refreshProviderForPath = { _, _ -> },
    preferredProviderProvider = { null },
    sourceProjectResolver = ::findOpenSourceProjectByPath,
  )

  internal constructor(
    launchPromptRequest: (AgentPromptLaunchRequest) -> AgentPromptLaunchResult,
    stateFlowProvider: () -> StateFlow<AgentSessionsState>,
    pathStateResolver: (AgentSessionsState, String) -> AgentSessionPathState?,
    refreshCatalogAndLoadNewlyOpened: () -> Unit,
    refreshProviderForPath: (String, AgentSessionProvider) -> Unit,
    preferredProviderProvider: () -> AgentSessionProvider?,
    sourceProjectResolver: (String) -> Project? = ::findOpenSourceProjectByPath,
  ) {
    this.launchPromptRequest = launchPromptRequest
    this.stateFlowProvider = stateFlowProvider
    this.pathStateResolver = pathStateResolver
    this.refreshCatalogAndLoadNewlyOpened = refreshCatalogAndLoadNewlyOpened
    this.refreshProviderForPath = refreshProviderForPath
    this.preferredProviderProvider = preferredProviderProvider
    this.sourceProjectResolver = sourceProjectResolver
  }

  override fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
    return launchPromptRequest(request)
  }

  override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String? {
    return listWorkingProjectPathCandidates(invocationData).firstOrNull()?.path
  }

  override fun resolveSourceProject(invocationData: AgentPromptInvocationData): Project? {
    val project = invocationData.project
    if (!AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProject(project)) {
      return project
    }

    val projectPath = resolveWorkingProjectPath(invocationData) ?: return null
    return sourceProjectResolver(projectPath)
  }

  override fun listWorkingProjectPathCandidates(invocationData: AgentPromptInvocationData): List<AgentPromptProjectPathCandidate> {
    return buildWorkingProjectPathCandidates(invocationData)
  }

  override fun observeExistingThreads(
    projectPath: String,
    provider: AgentSessionProvider,
  ): Flow<AgentPromptExistingThreadsSnapshot> {
    val normalizedPath = normalizeAgentWorkbenchPath(projectPath)
    return stateFlowProvider()
      .map { state ->
        val pathState = pathStateResolver(state, normalizedPath)
        buildSnapshot(pathState = pathState, provider = provider)
      }
      .distinctUntilChanged()
  }

  override suspend fun refreshExistingThreads(projectPath: String, provider: AgentSessionProvider) {
    val normalizedPath = normalizeAgentWorkbenchPath(projectPath)
    val pathState = pathStateResolver(stateFlowProvider().value, normalizedPath)
    when {
      pathState == null -> {
        refreshCatalogAndLoadNewlyOpened()
      }
      pathState.hasLoaded -> {
        refreshProviderForPath(normalizedPath, provider)
      }
      !pathState.isLoading -> {
        refreshCatalogAndLoadNewlyOpened()
      }
    }
  }
}

private fun buildWorkingProjectPathCandidates(invocationData: AgentPromptInvocationData): List<AgentPromptProjectPathCandidate> {
  val project = invocationData.project
  val candidatesByPath = LinkedHashMap<String, AgentPromptProjectPathCandidate>()

  fun addCandidate(path: String?, displayName: String?) {
    val normalizedPath = path
      ?.takeIf { it.isNotBlank() }
      ?.let(::normalizeAgentWorkbenchPath)
      ?: return
    if (normalizedPath.isBlank()) {
      return
    }
    if (AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProjectPath(normalizedPath)) {
      return
    }
    if (normalizedPath in candidatesByPath) {
      return
    }
    candidatesByPath[normalizedPath] = AgentPromptProjectPathCandidate(
      path = normalizedPath,
      displayName = displayName
        ?.takeIf { it.isNotBlank() }
        ?: normalizedPath.substringAfterLast('/').ifBlank { normalizedPath },
    )
  }

  val currentProjectPath = project.basePath
    ?.takeIf { it.isNotBlank() }
    ?.let(::normalizeAgentWorkbenchPath)
  if (!AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProject(project)) {
    addCandidate(path = currentProjectPath, displayName = project.name)
    return candidatesByPath.values.toList()
  }

  val dataContext = invocationData.dataContextOrNull()
  val promptProjectPathContext = getAgentPromptProjectPathContext(dataContext)
  addCandidate(path = promptProjectPathContext?.path, displayName = promptProjectPathContext?.displayName)

  val selectedChatPath = runCatching {
    project.service<AgentChatTabSelectionService>().selectedChatTab.value?.projectPath
  }.getOrNull()
  addCandidate(path = selectedChatPath, displayName = null)

  val recentProjectsManager = RecentProjectsManager.getInstance() as? RecentProjectsManagerBase
  recentProjectsManager
    ?.getRecentPaths()
    ?.forEach { recentPath ->
      val normalizedPath = normalizeAgentWorkbenchPath(recentPath)
      val displayName = recentProjectsManager.getDisplayName(normalizedPath)
        .takeIf { !it.isNullOrBlank() }
        ?: recentProjectsManager.getProjectName(normalizedPath).takeIf { it.isNotBlank() }
      addCandidate(path = normalizedPath, displayName = displayName)
    }

  return candidatesByPath.values.toList()
}

private fun AgentPromptInvocationData.dataContextOrNull(): DataContext? {
  return attributes[AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY] as? DataContext
}

private fun resolveTreeContextProjectPathCandidate(context: AgentSessionsTreePopupActionContext): AgentPromptProjectPathCandidate? {
  val path = resolveTreeContextPath(context.nodeId)
    ?.takeIf { it.isNotBlank() }
    ?.let(::normalizeAgentWorkbenchPath)
    ?: return null
  if (AgentWorkbenchDedicatedFrameProjectManager.isDedicatedProjectPath(path)) {
    return null
  }
  return AgentPromptProjectPathCandidate(
    path = path,
    displayName = resolveTreeContextDisplayName(context.node),
  )
}

private fun resolveTreeContextPath(treeId: SessionTreeId): String? {
  return when (treeId) {
    is SessionTreeId.Project -> treeId.path
    is SessionTreeId.Thread -> treeId.projectPath
    is SessionTreeId.SubAgent -> treeId.projectPath
    is SessionTreeId.Warning -> treeId.projectPath
    is SessionTreeId.Error -> treeId.projectPath
    is SessionTreeId.Empty -> treeId.projectPath
    SessionTreeId.MoreProjects -> null
    is SessionTreeId.MoreThreads -> treeId.projectPath
    is SessionTreeId.Worktree -> treeId.worktreePath
    is SessionTreeId.WorktreeThread -> treeId.worktreePath
    is SessionTreeId.WorktreeSubAgent -> treeId.worktreePath
    is SessionTreeId.WorktreeWarning -> treeId.worktreePath
    is SessionTreeId.WorktreeMoreThreads -> treeId.worktreePath
    is SessionTreeId.WorktreeError -> treeId.worktreePath
  }
}

private fun resolveTreeContextDisplayName(node: SessionTreeNode): String {
  return when (node) {
    is SessionTreeNode.Project -> node.project.name
    is SessionTreeNode.Thread -> node.project.name
    is SessionTreeNode.SubAgent -> node.project.name
    is SessionTreeNode.Error -> node.project.name
    is SessionTreeNode.Empty -> node.project.name
    is SessionTreeNode.MoreThreads -> node.project.name
    is SessionTreeNode.Worktree -> node.worktree.name
    is SessionTreeNode.Warning,
    is SessionTreeNode.MoreProjects -> ""
  }
}

private fun buildSnapshot(pathState: AgentSessionPathState?, provider: AgentSessionProvider): AgentPromptExistingThreadsSnapshot {
  if (pathState == null) {
    return AgentPromptExistingThreadsSnapshot(
      threads = emptyList(),
      isLoading = false,
      hasLoaded = false,
      hasError = false,
    )
  }

  val providerThreads = pathState.threads
    .asSequence()
    .filter { thread -> thread.provider == provider }
    .filter { thread -> !thread.archived }
    .sortedByDescending { thread -> thread.updatedAt }
    .toList()
  val hasProviderWarning = pathState.providerWarnings.any { warning -> warning.provider == provider }
  val hasError = pathState.errorMessage != null ||
                 (hasProviderWarning && providerThreads.isEmpty() && pathState.hasLoaded && !pathState.isLoading)

  return AgentPromptExistingThreadsSnapshot(
    threads = providerThreads,
    isLoading = pathState.isLoading,
    hasLoaded = pathState.hasLoaded,
    hasError = hasError,
  )
}
