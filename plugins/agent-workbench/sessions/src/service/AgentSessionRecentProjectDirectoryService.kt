// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import kotlinx.serialization.Serializable

internal interface AgentSessionRecentProjectDirectoryStore {
  fun getProjectDirectory(identityPath: String): String?

  fun syncRecentPaths(recentPaths: Set<String>, authoritativeProjectDirectoriesByPath: Map<String, String>)
}

@Service(Service.Level.APP)
@State(name = "AgentSessionRecentProjectDirectoryState", storages = [Storage(StoragePathMacros.CACHE_FILE)])
internal class AgentSessionRecentProjectDirectoryService
  : SerializablePersistentStateComponent<AgentSessionRecentProjectDirectoryService.RecentProjectDirectoryState>(RecentProjectDirectoryState()),
    AgentSessionRecentProjectDirectoryStore {

  override fun getProjectDirectory(identityPath: String): String? {
    return state.projectDirectoryByIdentityPath[normalizeAgentWorkbenchPath(identityPath)]
  }

  override fun syncRecentPaths(recentPaths: Set<String>, authoritativeProjectDirectoriesByPath: Map<String, String>) {
    val normalizedRecentPaths = recentPaths.mapTo(LinkedHashSet()) { path ->
      normalizeAgentWorkbenchPath(path)
    }
    val normalizedAuthoritativeDirectories = LinkedHashMap<String, String>()
    for ((path, projectDirectory) in authoritativeProjectDirectoriesByPath) {
      if (projectDirectory.isNotBlank()) {
        normalizedAuthoritativeDirectories[normalizeAgentWorkbenchPath(path)] = normalizeAgentWorkbenchPath(projectDirectory)
      }
    }
    val currentDirectories = state.projectDirectoryByIdentityPath
    val retainedDirectories = LinkedHashMap<String, String>()
    for ((path, projectDirectory) in currentDirectories) {
      if (path in normalizedRecentPaths) {
        retainedDirectories[path] = projectDirectory
      }
    }
    retainedDirectories.putAll(normalizedAuthoritativeDirectories)
    if (retainedDirectories == currentDirectories) {
      return
    }
    updateState { currentState ->
      currentState.copy(projectDirectoryByIdentityPath = retainedDirectories)
    }
  }

  @Serializable
  internal data class RecentProjectDirectoryState(
    @JvmField val projectDirectoryByIdentityPath: Map<String, String> = emptyMap(),
  )
}
