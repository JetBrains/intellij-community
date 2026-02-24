// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

internal interface SessionsTreeUiState {
  fun isProjectCollapsed(path: String): Boolean

  fun setProjectCollapsed(path: String, collapsed: Boolean): Boolean

  fun getVisibleThreadCount(path: String): Int

  fun incrementVisibleThreadCount(path: String, delta: Int): Boolean

  fun resetVisibleThreadCount(path: String): Boolean

  fun getOpenProjectThreadPreviews(path: String): List<AgentSessionThreadPreview>?

  fun setOpenProjectThreadPreviews(path: String, threads: List<AgentSessionThreadPreview>): Boolean

  fun retainOpenProjectThreadPreviews(paths: Set<String>): Boolean
}

internal const val DEFAULT_VISIBLE_CLOSED_PROJECT_COUNT: Int = 3
internal const val DEFAULT_VISIBLE_THREAD_COUNT: Int = 3
internal const val OPEN_PROJECT_THREAD_CACHE_LIMIT: Int = 10

internal class InMemorySessionsTreeUiState : SessionsTreeUiState {
  private val collapsedProjectPaths = LinkedHashSet<String>()
  private val visibleThreadCountByProject = LinkedHashMap<String, Int>()
  private val openProjectThreadPreviewsByProject = LinkedHashMap<String, List<AgentSessionThreadPreview>>()

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

  override fun getOpenProjectThreadPreviews(path: String): List<AgentSessionThreadPreview>? {
    return openProjectThreadPreviewsByProject[normalizeAgentWorkbenchPath(path)]
  }

  override fun setOpenProjectThreadPreviews(path: String, threads: List<AgentSessionThreadPreview>): Boolean {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val normalizedThreads = normalizeOpenProjectThreadPreviewList(threads)
    val current = openProjectThreadPreviewsByProject[normalizedPath]
    if (current == normalizedThreads) return false
    openProjectThreadPreviewsByProject[normalizedPath] = normalizedThreads
    return true
  }

  override fun retainOpenProjectThreadPreviews(paths: Set<String>): Boolean {
    val normalizedPaths = paths.mapTo(LinkedHashSet()) { normalizeAgentWorkbenchPath(it) }
    var changed = false
    val iterator = openProjectThreadPreviewsByProject.keys.iterator()
    while (iterator.hasNext()) {
      val key = iterator.next()
      if (key !in normalizedPaths) {
        iterator.remove()
        changed = true
      }
    }
    return changed
  }
}

@Service(Service.Level.APP)
@State(name = "CodexSessionsTreeUiState", storages = [Storage(StoragePathMacros.CACHE_FILE)])
internal class AgentSessionsTreeUiStateService
  : SerializablePersistentStateComponent<AgentSessionsTreeUiStateService.SessionsTreeUiStateState>(SessionsTreeUiStateState()),
    SessionsTreeUiState {

  private val _lastUsedProviderFlow = MutableStateFlow(getLastUsedProvider())
  val lastUsedProviderFlow: StateFlow<AgentSessionProvider?> = _lastUsedProviderFlow.asStateFlow()
  private val _claudeQuotaHintEligibleFlow = MutableStateFlow(state.claudeQuotaHintEligible)
  val claudeQuotaHintEligibleFlow: StateFlow<Boolean> = _claudeQuotaHintEligibleFlow.asStateFlow()
  private val _claudeQuotaHintAcknowledgedFlow = MutableStateFlow(state.claudeQuotaHintAcknowledged)
  val claudeQuotaHintAcknowledgedFlow: StateFlow<Boolean> = _claudeQuotaHintAcknowledgedFlow.asStateFlow()

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

  override fun getOpenProjectThreadPreviews(path: String): List<AgentSessionThreadPreview>? {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val previews = state.openProjectThreadPreviewsByProject[normalizedPath] ?: return null
    return previews.map { preview ->
      AgentSessionThreadPreview(
        id = preview.id,
        title = threadDisplayTitle(threadId = preview.id, title = preview.title),
        updatedAt = preview.updatedAt,
        provider = preview.provider
          ?.let(AgentSessionProvider::fromOrNull)
          ?: AgentSessionProvider.CODEX,
      )
    }
  }

  override fun setOpenProjectThreadPreviews(path: String, threads: List<AgentSessionThreadPreview>): Boolean {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val normalizedPreviews = normalizeOpenProjectThreadPreviewList(threads)
      .map { thread ->
        ThreadPreviewState(
          id = thread.id,
          title = thread.title,
          updatedAt = thread.updatedAt,
          provider = thread.provider.value,
        )
      }
    val current = state.openProjectThreadPreviewsByProject[normalizedPath]
    if (current == normalizedPreviews) {
      return false
    }
    updateState { currentState ->
      val updated = currentState.openProjectThreadPreviewsByProject.toMutableMap()
      updated[normalizedPath] = normalizedPreviews
      currentState.copy(openProjectThreadPreviewsByProject = updated)
    }
    return true
  }

  override fun retainOpenProjectThreadPreviews(paths: Set<String>): Boolean {
    val normalizedPaths = paths.mapTo(LinkedHashSet()) { normalizeAgentWorkbenchPath(it) }
    val current = state.openProjectThreadPreviewsByProject
    if (current.keys.all { it in normalizedPaths }) {
      return false
    }
    updateState { currentState ->
      val filtered = currentState.openProjectThreadPreviewsByProject
        .filterKeys { it in normalizedPaths }
      currentState.copy(openProjectThreadPreviewsByProject = filtered)
    }
    return true
  }

  private fun updateNormalizedPathSet(
    path: String,
    includePath: Boolean,
    currentSet: (SessionsTreeUiStateState) -> Set<String>,
    setUpdated: (SessionsTreeUiStateState, Set<String>) -> SessionsTreeUiStateState,
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

  fun getLastUsedProvider(): AgentSessionProvider? {
    val id = state.lastUsedProvider ?: return null
    return AgentSessionProvider.fromOrNull(id)
  }

  fun setLastUsedProvider(provider: AgentSessionProvider) {
    updateState { it.copy(lastUsedProvider = provider.value) }
    _lastUsedProviderFlow.value = provider
  }

  fun markClaudeQuotaHintEligible() {
    if (state.claudeQuotaHintEligible) return
    updateState { it.copy(claudeQuotaHintEligible = true) }
    _claudeQuotaHintEligibleFlow.value = true
  }

  fun acknowledgeClaudeQuotaHint() {
    if (state.claudeQuotaHintAcknowledged) return
    updateState { it.copy(claudeQuotaHintAcknowledged = true) }
    _claudeQuotaHintAcknowledgedFlow.value = true
  }

  override fun loadState(state: SessionsTreeUiStateState) {
    super.loadState(state)
    _lastUsedProviderFlow.value = state.lastUsedProvider?.let(AgentSessionProvider::fromOrNull)
    _claudeQuotaHintEligibleFlow.value = state.claudeQuotaHintEligible
    _claudeQuotaHintAcknowledgedFlow.value = state.claudeQuotaHintAcknowledged
  }

  @Serializable
  internal data class SessionsTreeUiStateState(
    @JvmField val collapsedProjectPaths: Set<String> = emptySet(),
    @JvmField val visibleThreadCountByProject: Map<String, Int> = emptyMap(),
    @JvmField val openProjectThreadPreviewsByProject: Map<String, List<ThreadPreviewState>> = emptyMap(),
    @JvmField val lastUsedProvider: String? = null,
    @JvmField val claudeQuotaHintEligible: Boolean = false,
    @JvmField val claudeQuotaHintAcknowledged: Boolean = false,
  )

  @Serializable
  internal data class ThreadPreviewState(
    @JvmField val id: String,
    @JvmField val title: String,
    @JvmField val updatedAt: Long,
    @JvmField val provider: String? = null,
  )
}

private fun normalizeVisibleThreadCount(value: Int): Int {
  return value.coerceAtLeast(DEFAULT_VISIBLE_THREAD_COUNT)
}

private fun normalizeOpenProjectThreadPreviewList(threads: List<AgentSessionThreadPreview>): List<AgentSessionThreadPreview> {
  return threads
    .map { thread ->
      thread.copy(title = threadDisplayTitle(threadId = thread.id, title = thread.title))
    }
    .sortedByDescending { it.updatedAt }
    .take(OPEN_PROJECT_THREAD_CACHE_LIMIT)
}
