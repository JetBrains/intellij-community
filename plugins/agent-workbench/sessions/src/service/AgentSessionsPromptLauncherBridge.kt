// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatTabSelectionService
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.actions.AgentSessionsTreePopupActionContext
import com.intellij.agent.workbench.sessions.actions.AgentSessionsTreePopupDataKeys
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.prompt.AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptExistingThreadsSnapshot
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchResult
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLauncherBridge
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderWarning
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.tree.SessionTreeNode
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class AgentSessionsPromptLauncherBridge(
  private val sessionsServiceProvider: () -> AgentSessionsService = { service() },
) : AgentPromptLauncherBridge {
  override fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
    return sessionsServiceProvider().launchPromptRequest(request)
  }

  override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String? {
    return listWorkingProjectPathCandidates(invocationData).firstOrNull()?.path
  }

  override fun listWorkingProjectPathCandidates(invocationData: AgentPromptInvocationData): List<AgentPromptProjectPathCandidate> {
    return buildWorkingProjectPathCandidates(invocationData)
  }

  override fun observeExistingThreads(
    projectPath: String,
    provider: AgentSessionProvider,
  ): Flow<AgentPromptExistingThreadsSnapshot> {
    val normalizedPath = normalizeAgentWorkbenchPath(projectPath)
    return sessionsServiceProvider().state
      .map { state ->
        val pathState = resolvePathState(state = state, normalizedPath = normalizedPath)
        buildSnapshot(pathState = pathState, provider = provider)
      }
      .distinctUntilChanged()
  }

  override suspend fun refreshExistingThreads(projectPath: String, provider: AgentSessionProvider) {
    val normalizedPath = normalizeAgentWorkbenchPath(projectPath)
    val sessionsService = sessionsServiceProvider()
    val pathState = resolvePathState(state = sessionsService.state.value, normalizedPath = normalizedPath)
    when {
      pathState == null -> {
        sessionsService.refreshCatalogAndLoadNewlyOpened()
      }
      pathState.hasLoaded -> {
        sessionsService.refreshProviderForPath(path = normalizedPath, provider = provider)
      }
      !pathState.isLoading -> {
        sessionsService.refreshCatalogAndLoadNewlyOpened()
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

  val treeContextCandidate = invocationData.dataContextOrNull()
    ?.getData(AgentSessionsTreePopupDataKeys.CONTEXT)
    ?.let(::resolveTreeContextProjectPathCandidate)
  addCandidate(path = treeContextCandidate?.path, displayName = treeContextCandidate?.displayName)

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

private data class PromptPathState(
  @JvmField val threads: List<AgentSessionThread>,
  @JvmField val isLoading: Boolean,
  @JvmField val hasLoaded: Boolean,
  @JvmField val errorMessage: String?,
  @JvmField val providerWarnings: List<AgentSessionProviderWarning>,
)

private fun resolvePathState(state: AgentSessionsState, normalizedPath: String): PromptPathState? {
  state.projects.firstOrNull { project -> project.path == normalizedPath }?.let { project ->
    return PromptPathState(
      threads = project.threads,
      isLoading = project.isLoading,
      hasLoaded = project.hasLoaded,
      errorMessage = project.errorMessage,
      providerWarnings = project.providerWarnings,
    )
  }

  state.projects.forEach { project ->
    val worktree = project.worktrees.firstOrNull { candidate -> candidate.path == normalizedPath } ?: return@forEach
    return PromptPathState(
      threads = worktree.threads,
      isLoading = worktree.isLoading,
      hasLoaded = worktree.hasLoaded,
      errorMessage = worktree.errorMessage,
      providerWarnings = worktree.providerWarnings,
    )
  }

  return null
}

private fun buildSnapshot(pathState: PromptPathState?, provider: AgentSessionProvider): AgentPromptExistingThreadsSnapshot {
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
