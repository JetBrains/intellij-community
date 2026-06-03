// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.state

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.normalizeAgentSessionTitle
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import kotlinx.serialization.Serializable

internal interface AgentSessionThreadTitleOverrides {
  fun getTitle(path: String, provider: AgentSessionProvider, threadId: String): String?

  fun setTitle(path: String, provider: AgentSessionProvider, threadId: String, title: String): Boolean

  fun retainPaths(paths: Set<String>): Boolean
}

internal class InMemoryAgentSessionThreadTitleOverrides : AgentSessionThreadTitleOverrides {
  private val titlesByPath = LinkedHashMap<String, MutableMap<String, MutableMap<String, String>>>()

  override fun getTitle(path: String, provider: AgentSessionProvider, threadId: String): String? {
    val normalizedKey = normalizeTitleOverrideKey(path = path, provider = provider, threadId = threadId) ?: return null
    return titlesByPath[normalizedKey.path]?.get(normalizedKey.providerId)?.get(normalizedKey.threadId)
  }

  override fun setTitle(path: String, provider: AgentSessionProvider, threadId: String, title: String): Boolean {
    val normalizedKey = normalizeTitleOverrideKey(path = path, provider = provider, threadId = threadId) ?: return false
    val normalizedTitle = normalizeAgentSessionTitle(title) ?: return false
    val titlesByProvider = titlesByPath.getOrPut(normalizedKey.path) { LinkedHashMap() }
    val titlesByThread = titlesByProvider.getOrPut(normalizedKey.providerId) { LinkedHashMap() }
    if (titlesByThread[normalizedKey.threadId] == normalizedTitle) {
      return false
    }
    titlesByThread[normalizedKey.threadId] = normalizedTitle
    return true
  }

  override fun retainPaths(paths: Set<String>): Boolean {
    val normalizedPaths = paths.mapTo(LinkedHashSet()) { normalizeAgentWorkbenchPath(it) }
    var changed = false
    val iterator = titlesByPath.keys.iterator()
    while (iterator.hasNext()) {
      val path = iterator.next()
      if (path !in normalizedPaths) {
        iterator.remove()
        changed = true
      }
    }
    return changed
  }
}

@Service(Service.Level.APP)
@State(name = "AgentSessionThreadTitleOverrideState", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)])
internal class AgentSessionThreadTitleOverrideStateService
  : SerializablePersistentStateComponent<AgentSessionThreadTitleOverrideStateService.TitleOverrideState>(TitleOverrideState()),
    AgentSessionThreadTitleOverrides {

  override fun getTitle(path: String, provider: AgentSessionProvider, threadId: String): String? {
    val normalizedKey = normalizeTitleOverrideKey(path = path, provider = provider, threadId = threadId) ?: return null
    return state.titlesByPath[normalizedKey.path]?.get(normalizedKey.providerId)?.get(normalizedKey.threadId)
  }

  override fun setTitle(path: String, provider: AgentSessionProvider, threadId: String, title: String): Boolean {
    val normalizedKey = normalizeTitleOverrideKey(path = path, provider = provider, threadId = threadId) ?: return false
    val normalizedTitle = normalizeAgentSessionTitle(title) ?: return false
    if (getTitle(path = normalizedKey.path, provider = provider, threadId = normalizedKey.threadId) == normalizedTitle) {
      return false
    }
    updateState { current ->
      val titlesByPath = current.titlesByPath.toMutableMap()
      val titlesByProvider = titlesByPath[normalizedKey.path].orEmpty().toMutableMap()
      val titlesByThread = titlesByProvider[normalizedKey.providerId].orEmpty().toMutableMap()
      titlesByThread[normalizedKey.threadId] = normalizedTitle
      titlesByProvider[normalizedKey.providerId] = titlesByThread
      titlesByPath[normalizedKey.path] = titlesByProvider
      current.copy(titlesByPath = titlesByPath)
    }
    return true
  }

  override fun retainPaths(paths: Set<String>): Boolean {
    val normalizedPaths = paths.mapTo(LinkedHashSet()) { normalizeAgentWorkbenchPath(it) }
    val current = state.titlesByPath
    if (current.keys.all { it in normalizedPaths }) {
      return false
    }
    updateState { currentState ->
      currentState.copy(titlesByPath = currentState.titlesByPath.filterKeys { it in normalizedPaths })
    }
    return true
  }

  @Serializable
  internal data class TitleOverrideState(
    @JvmField val titlesByPath: Map<String, Map<String, Map<String, String>>> = emptyMap(),
  )
}

internal fun AgentSessionThreadTitleOverrides.applyTitleOverrides(
  path: String,
  threads: List<AgentSessionThread>,
): List<AgentSessionThread> {
  if (threads.isEmpty()) {
    return threads
  }
  var changed = false
  val updatedThreads = threads.map { thread ->
    val titleOverride = getTitle(path = path, provider = thread.provider, threadId = thread.id) ?: return@map thread
    if (titleOverride == thread.title) {
      thread
    }
    else {
      changed = true
      thread.copy(title = titleOverride)
    }
  }
  return if (changed) updatedThreads else threads
}

internal fun AgentSessionThreadTitleOverrides.applyTitleOverrides(
  path: String,
  provider: AgentSessionProvider,
  candidates: List<AgentSessionRebindCandidate>,
): List<AgentSessionRebindCandidate> {
  if (candidates.isEmpty()) {
    return candidates
  }
  var changed = false
  val updatedCandidates = candidates.map { candidate ->
    val titleOverride = getTitle(path = path, provider = provider, threadId = candidate.threadId) ?: return@map candidate
    if (titleOverride == candidate.title) {
      candidate
    }
    else {
      changed = true
      candidate.copy(title = titleOverride)
    }
  }
  return if (changed) updatedCandidates else candidates
}

private data class ThreadTitleOverrideKey(
  @JvmField val path: String,
  @JvmField val providerId: String,
  @JvmField val threadId: String,
)

private fun normalizeTitleOverrideKey(
  path: String,
  provider: AgentSessionProvider,
  threadId: String,
): ThreadTitleOverrideKey? {
  val normalizedPath = normalizeAgentWorkbenchPath(path).takeIf { it.isNotBlank() } ?: return null
  val normalizedThreadId = threadId.trim().takeIf { it.isNotEmpty() } ?: return null
  return ThreadTitleOverrideKey(
    path = normalizedPath,
    providerId = provider.value,
    threadId = normalizedThreadId,
  )
}
