// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptExistingThreadsSnapshot
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchResult
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLauncherBridge
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderWarning
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
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
