// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.sessions.core.paths.resolveAgentWorkbenchProjectDirectory
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.model.hasAnyProviderSnapshot
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AgentSessionOnDemandLoadSupport(
  private val serviceScope: CoroutineScope,
  private val stateStore: AgentSessionsStateStore,
  private val threadLoadSupport: AgentSessionThreadLoadSupport,
  private val sessionSourcesProvider: () -> List<AgentSessionSource>,
  loadingDelayMs: Long = DEFAULT_AGENT_SESSION_LOADING_DELAY_MS,
) {
  private val onDemandMutex = Mutex()
  private val onDemandLoading = LinkedHashSet<String>()
  private val onDemandWorktreeLoading = LinkedHashSet<String>()
  private val pathLoadController = AgentSessionPathLoadController(loadingDelayMs)

  fun loadProjectThreadsOnDemand(path: String) {
    serviceScope.launch(Dispatchers.IO) {
      val normalized = normalizeAgentWorkbenchPath(path)
      if (!markOnDemandLoading(normalized)) return@launch
      try {
        val loadingProviderLoadStates = buildLoadingProviderLoadStates(sessionSourcesProvider().map { source -> source.provider })
        val result = pathLoadController.runWithDelayedLoading(
          providerLoadStates = { loadingProviderLoadStates },
          publishLoading = { providerLoadStates ->
            stateStore.updateProject(normalized) { project -> project.withLoadingProviderLoadStates(providerLoadStates) }
          },
        ) {
          threadLoadSupport.loadThreadsFromClosedProject(
            path = normalized,
            projectDirectory = resolveClosedProjectDirectory(path = normalized),
          )
        }
        stateStore.updateProject(normalized) { project ->
          project.copy(
            threads = result.threads,
            errorMessage = result.errorMessage,
            providerWarnings = result.providerWarnings,
            providerLoadStates = result.providerLoadStates,
            providersWithUnknownThreadCount = result.providersWithUnknownThreadCount,
          )
        }
      }
      finally {
        clearOnDemandLoading(normalized)
      }
    }
  }

  fun loadWorktreeThreadsOnDemand(projectPath: String, worktreePath: String) {
    serviceScope.launch(Dispatchers.IO) {
      val normalizedProject = normalizeAgentWorkbenchPath(projectPath)
      val normalizedWorktree = normalizeAgentWorkbenchPath(worktreePath)
      if (!markWorktreeOnDemandLoading(normalizedProject, normalizedWorktree)) return@launch
      try {
        val loadingProviderLoadStates = buildLoadingProviderLoadStates(sessionSourcesProvider().map { source -> source.provider })
        val result = pathLoadController.runWithDelayedLoading(
          providerLoadStates = { loadingProviderLoadStates },
          publishLoading = { providerLoadStates ->
            stateStore.updateWorktree(normalizedProject, normalizedWorktree) { worktree ->
              worktree.withLoadingProviderLoadStates(providerLoadStates)
            }
          },
        ) {
          threadLoadSupport.loadThreadsFromClosedProject(
            path = normalizedWorktree,
            projectDirectory = resolveClosedProjectDirectory(path = normalizedWorktree),
          )
        }
        stateStore.updateWorktree(normalizedProject, normalizedWorktree) { worktree ->
          worktree.copy(
            threads = result.threads,
            errorMessage = result.errorMessage,
            providerWarnings = result.providerWarnings,
            providerLoadStates = result.providerLoadStates,
            providersWithUnknownThreadCount = result.providersWithUnknownThreadCount,
          )
        }
      }
      finally {
        clearWorktreeOnDemandLoading(normalizedWorktree)
      }
    }
  }

  private suspend fun markOnDemandLoading(path: String): Boolean {
    return onDemandMutex.withLock {
      val project = stateStore.state.value.projects.firstOrNull { it.path == path } ?: return@withLock false
      if (project.isOpen || project.isLoading || project.hasAnyProviderSnapshot()) return@withLock false
      if (!onDemandLoading.add(path)) return@withLock false
      true
    }
  }

  private suspend fun clearOnDemandLoading(path: String) {
    onDemandMutex.withLock {
      onDemandLoading.remove(path)
    }
  }

  private suspend fun markWorktreeOnDemandLoading(projectPath: String, worktreePath: String): Boolean {
    return onDemandMutex.withLock {
      val project = stateStore.state.value.projects.firstOrNull { it.path == projectPath } ?: return@withLock false
      val worktree = project.worktrees.firstOrNull { it.path == worktreePath } ?: return@withLock false
      if (worktree.isLoading || worktree.hasAnyProviderSnapshot()) return@withLock false
      if (!onDemandWorktreeLoading.add(worktreePath)) return@withLock false
      true
    }
  }

  private suspend fun clearWorktreeOnDemandLoading(worktreePath: String) {
    onDemandMutex.withLock {
      onDemandWorktreeLoading.remove(worktreePath)
    }
  }

  private fun resolveClosedProjectDirectory(path: String): String? {
    return stateStore.findProjectDirectory(path)
           ?: resolveAgentWorkbenchProjectDirectory(identityPath = path)
  }
}
