// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.state

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import kotlinx.serialization.Serializable

internal interface SessionTreeUiState {
  fun isProjectCollapsed(path: String): Boolean

  fun setProjectCollapsed(path: String, collapsed: Boolean): Boolean

  fun getVisibleThreadCount(path: String): Int

  fun incrementVisibleThreadCount(path: String, delta: Int): Boolean

  fun resetVisibleThreadCount(path: String): Boolean
}

internal const val DEFAULT_VISIBLE_CLOSED_PROJECT_COUNT: Int = 3
internal const val DEFAULT_VISIBLE_THREAD_COUNT: Int = 3

internal class InMemorySessionTreeUiState : SessionTreeUiState {
  private val collapsedProjectPaths = LinkedHashSet<String>()
  private val visibleThreadCountByProject = LinkedHashMap<String, Int>()

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

  override fun getVisibleThreadCount(path: String): Int {
    val normalized = normalizeAgentWorkbenchPath(path)
    return visibleThreadCountByProject[normalized] ?: DEFAULT_VISIBLE_THREAD_COUNT
  }

  override fun incrementVisibleThreadCount(path: String, delta: Int): Boolean {
    if (delta <= 0) return false
    val normalized = normalizeAgentWorkbenchPath(path)
    val current = visibleThreadCountByProject[normalized] ?: DEFAULT_VISIBLE_THREAD_COUNT
    val updated = normalizeVisibleThreadCount(current + delta)
    if (updated == current) return false
    return if (updated == DEFAULT_VISIBLE_THREAD_COUNT) {
      visibleThreadCountByProject.remove(normalized) != null
    }
    else {
      visibleThreadCountByProject.put(normalized, updated) != updated
    }
  }

  override fun resetVisibleThreadCount(path: String): Boolean {
    return visibleThreadCountByProject.remove(normalizeAgentWorkbenchPath(path)) != null
  }
}

@Service(Service.Level.APP)
@State(name = "AgentSessionTreeUiState", storages = [Storage(StoragePathMacros.CACHE_FILE)])
internal class AgentSessionTreeUiStateService
  : SerializablePersistentStateComponent<AgentSessionTreeUiStateService.SessionTreeUiStateState>(SessionTreeUiStateState()),
    SessionTreeUiState {

  override fun isProjectCollapsed(path: String): Boolean {
    return normalizeAgentWorkbenchPath(path) in state.collapsedProjectPaths
  }

  override fun setProjectCollapsed(path: String, collapsed: Boolean): Boolean {
    return updateNormalizedPathSet(
      path = path,
      includePath = collapsed,
      currentSet = { it.collapsedProjectPaths },
      setUpdated = { current, updated -> current.copy(collapsedProjectPaths = updated) },
    )
  }

  override fun getVisibleThreadCount(path: String): Int {
    val normalized = normalizeAgentWorkbenchPath(path)
    return state.visibleThreadCountByProject[normalized] ?: DEFAULT_VISIBLE_THREAD_COUNT
  }

  override fun incrementVisibleThreadCount(path: String, delta: Int): Boolean {
    if (delta <= 0) return false
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val current = state.visibleThreadCountByProject[normalizedPath] ?: DEFAULT_VISIBLE_THREAD_COUNT
    val updated = normalizeVisibleThreadCount(current + delta)
    return setVisibleThreadCountInternal(normalizedPath, updated)
  }

  override fun resetVisibleThreadCount(path: String): Boolean {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    return setVisibleThreadCountInternal(normalizedPath, DEFAULT_VISIBLE_THREAD_COUNT)
  }

  private fun updateNormalizedPathSet(
    path: String,
    includePath: Boolean,
    currentSet: (SessionTreeUiStateState) -> Set<String>,
    setUpdated: (SessionTreeUiStateState, Set<String>) -> SessionTreeUiStateState,
  ): Boolean {
    val normalized = normalizeAgentWorkbenchPath(path)
    if ((normalized in currentSet(state)) == includePath) {
      return false
    }
    updateState { current ->
      val updated = currentSet(current).toMutableSet()
      if (includePath) {
        updated.add(normalized)
      }
      else {
        updated.remove(normalized)
      }
      setUpdated(current, updated)
    }
    return true
  }

  private fun setVisibleThreadCountInternal(normalizedPath: String, visibleCount: Int): Boolean {
    val normalizedCount = normalizeVisibleThreadCount(visibleCount)
    val currentCount = state.visibleThreadCountByProject[normalizedPath] ?: DEFAULT_VISIBLE_THREAD_COUNT
    if (currentCount == normalizedCount) {
      return false
    }
    updateState { current ->
      val updated = current.visibleThreadCountByProject.toMutableMap()
      if (normalizedCount == DEFAULT_VISIBLE_THREAD_COUNT) {
        updated.remove(normalizedPath)
      }
      else {
        updated[normalizedPath] = normalizedCount
      }
      current.copy(visibleThreadCountByProject = updated)
    }
    return true
  }

  @Serializable
  internal data class SessionTreeUiStateState(
    @JvmField val collapsedProjectPaths: Set<String> = emptySet(),
    @JvmField val visibleThreadCountByProject: Map<String, Int> = emptyMap(),
  )
}

private fun normalizeVisibleThreadCount(value: Int): Int {
  return value.coerceAtLeast(DEFAULT_VISIBLE_THREAD_COUNT)
}
