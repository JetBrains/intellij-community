// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.state

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import kotlinx.serialization.Serializable

interface SessionTreeUiState {
  fun isProjectCollapsed(path: String): Boolean

  fun setProjectCollapsed(path: String, collapsed: Boolean): Boolean
}

class InMemorySessionTreeUiState : SessionTreeUiState {
  private val collapsedProjectPaths = LinkedHashSet<String>()

  override fun isProjectCollapsed(path: String): Boolean {
    return normalizeAgentWorkbenchPath(path) in collapsedProjectPaths
  }

  override fun setProjectCollapsed(path: String, collapsed: Boolean): Boolean {
    val normalized = normalizeAgentWorkbenchPath(path)
    return if (collapsed) {
      collapsedProjectPaths.add(normalized)
    }
    else {
      collapsedProjectPaths.remove(normalized)
    }
  }
}

@Service(Service.Level.APP)
@State(name = "AgentSessionTreeUiState", storages = [Storage(StoragePathMacros.CACHE_FILE)])
class AgentSessionTreeUiStateService
  : SerializablePersistentStateComponent<AgentSessionTreeUiStateService.SessionTreeUiStateState>(SessionTreeUiStateState()),
    SessionTreeUiState {

  override fun isProjectCollapsed(path: String): Boolean {
    return normalizeAgentWorkbenchPath(path) in state.collapsedProjectPaths
  }

  override fun setProjectCollapsed(path: String, collapsed: Boolean): Boolean {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    if ((normalizedPath in state.collapsedProjectPaths) == collapsed) {
      return false
    }
    updateState { current ->
      val updated = current.collapsedProjectPaths.toMutableSet()
      if (collapsed) {
        updated.add(normalizedPath)
      }
      else {
        updated.remove(normalizedPath)
      }
      current.copy(collapsedProjectPaths = updated)
    }
    return true
  }

  @Serializable
  data class SessionTreeUiStateState(
    @JvmField val collapsedProjectPaths: Set<String> = emptySet(),
  )
}
