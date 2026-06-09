// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.TestOnly

data class AgentSessionThreadPresentationKey(
  @JvmField val normalizedProjectPath: String,
  val provider: AgentSessionProvider,
  @JvmField val threadId: String,
) {
  companion object {
    fun create(projectPath: String, provider: AgentSessionProvider, threadId: String): AgentSessionThreadPresentationKey? {
      val normalizedProjectPath = normalizeAgentWorkbenchPath(projectPath).takeIf { it.isNotBlank() } ?: return null
      val normalizedThreadId = threadId.trim().takeIf { it.isNotEmpty() } ?: return null
      return AgentSessionThreadPresentationKey(
        normalizedProjectPath = normalizedProjectPath,
        provider = provider,
        threadId = normalizedThreadId,
      )
    }
  }
}

data class AgentSessionThreadPresentation(
  @JvmField val title: @NlsSafe String,
  @JvmField val activity: AgentThreadActivity,
)

data class AgentSessionThreadPresentationChangeSet(
  @JvmField val changedKeys: Set<AgentSessionThreadPresentationKey>,
  @JvmField val removedKeys: Set<AgentSessionThreadPresentationKey>,
) {
  companion object {
    val EMPTY: AgentSessionThreadPresentationChangeSet = AgentSessionThreadPresentationChangeSet(
      changedKeys = emptySet(),
      removedKeys = emptySet(),
    )
  }

  val isEmpty: Boolean
    get() = changedKeys.isEmpty() && removedKeys.isEmpty()
}

data class AgentSessionThreadActivityPresentationUpdate(
  @JvmField val path: String,
  @JvmField val threadId: String,
  @JvmField val activity: AgentThreadActivity,
)

@Service(Service.Level.APP)
class AgentSessionThreadPresentationModel {
  private val lock = Any()
  private val mutableState = MutableStateFlow<Map<AgentSessionThreadPresentationKey, AgentSessionThreadPresentation>>(emptyMap())
  private val mutableChanges = MutableSharedFlow<AgentSessionThreadPresentationChangeSet>(extraBufferCapacity = 64)

  val state: StateFlow<Map<AgentSessionThreadPresentationKey, AgentSessionThreadPresentation>> = mutableState.asStateFlow()
  val changes: SharedFlow<AgentSessionThreadPresentationChangeSet> = mutableChanges.asSharedFlow()

  fun snapshot(): Map<AgentSessionThreadPresentationKey, AgentSessionThreadPresentation> = mutableState.value

  fun resolve(key: AgentSessionThreadPresentationKey): AgentSessionThreadPresentation? = mutableState.value[key]

  fun updateThread(
    path: String,
    provider: AgentSessionProvider,
    threadId: String,
    title: String,
    activity: AgentThreadActivity?,
  ): AgentSessionThreadPresentationChangeSet {
    val key = AgentSessionThreadPresentationKey.create(projectPath = path, provider = provider, threadId = threadId)
              ?: return AgentSessionThreadPresentationChangeSet.EMPTY
    val normalizedTitle = normalizeAgentSessionTitle(title) ?: title
    return putMerged(mapOf(key to (normalizedTitle to activity)))
  }

  fun updateActivityHints(
    provider: AgentSessionProvider,
    updates: Collection<AgentSessionThreadActivityPresentationUpdate>,
  ): AgentSessionThreadPresentationChangeSet {
    if (updates.isEmpty()) {
      return AgentSessionThreadPresentationChangeSet.EMPTY
    }
    val inputs = LinkedHashMap<AgentSessionThreadPresentationKey, Pair<String?, AgentThreadActivity?>>(updates.size)
    for ((path, threadId, activity) in updates) {
      val key = AgentSessionThreadPresentationKey.create(projectPath = path, provider = provider, threadId = threadId) ?: continue
      inputs[key] = null to activity
    }
    return putMerged(inputs)
  }

  fun updateProviderSnapshot(
    provider: AgentSessionProvider,
    authoritativePaths: Set<String>,
    threadsByPath: Map<String, List<AgentSessionThread>>,
  ): AgentSessionThreadPresentationChangeSet {
    if (authoritativePaths.isEmpty() && threadsByPath.isEmpty()) {
      return AgentSessionThreadPresentationChangeSet.EMPTY
    }

    val normalizedAuthoritativePaths = authoritativePaths
      .asSequence()
      .map(::normalizeAgentWorkbenchPath)
      .filter(String::isNotBlank)
      .toCollection(LinkedHashSet())
    val presentationsByKey = LinkedHashMap<AgentSessionThreadPresentationKey, AgentSessionThreadPresentation>()
    for ((path, threads) in threadsByPath) {
      threads.forEach { thread ->
        if (thread.provider != provider) return@forEach
        val key =
          AgentSessionThreadPresentationKey.create(projectPath = path, provider = thread.provider, threadId = thread.id) ?: return@forEach
        presentationsByKey[key] = AgentSessionThreadPresentation(
          title = normalizeAgentSessionTitle(thread.title) ?: thread.title,
          activity = thread.activity,
        )
      }
    }

    return updateState { current ->
      val next = LinkedHashMap<AgentSessionThreadPresentationKey, AgentSessionThreadPresentation>(current.size + presentationsByKey.size)
      val changedKeys = LinkedHashSet<AgentSessionThreadPresentationKey>()
      val removedKeys = LinkedHashSet<AgentSessionThreadPresentationKey>()

      for ((key, presentation) in current) {
        if (key.provider == provider && key.normalizedProjectPath in normalizedAuthoritativePaths && key !in presentationsByKey) {
          removedKeys.add(key)
          continue
        }
        next[key] = presentation
      }

      for ((key, presentation) in presentationsByKey) {
        if (next[key] == presentation) continue
        next[key] = presentation
        changedKeys.add(key)
      }

      PresentationStateUpdate(next = next, changedKeys = changedKeys, removedKeys = removedKeys)
    }
  }

  fun remove(keys: Set<AgentSessionThreadPresentationKey>): AgentSessionThreadPresentationChangeSet {
    if (keys.isEmpty()) {
      return AgentSessionThreadPresentationChangeSet.EMPTY
    }
    return updateState { current ->
      val next = LinkedHashMap(current)
      val removedKeys = LinkedHashSet<AgentSessionThreadPresentationKey>()
      for (key in keys) {
        if (next.remove(key) != null) {
          removedKeys.add(key)
        }
      }
      PresentationStateUpdate(next = next, changedKeys = emptySet(), removedKeys = removedKeys)
    }
  }

  @TestOnly
  fun replaceForTests(presentationsByKey: Map<AgentSessionThreadPresentationKey, AgentSessionThreadPresentation>) {
    synchronized(lock) {
      mutableState.value = LinkedHashMap(presentationsByKey)
    }
  }

  @TestOnly
  fun clearForTests() {
    synchronized(lock) {
      mutableState.value = emptyMap()
    }
  }

  private fun putMerged(
    updates: Map<AgentSessionThreadPresentationKey, Pair<String?, AgentThreadActivity?>>,
  ): AgentSessionThreadPresentationChangeSet {
    if (updates.isEmpty()) return AgentSessionThreadPresentationChangeSet.EMPTY
    return updateState { current ->
      val next = LinkedHashMap(current)
      val changedKeys = LinkedHashSet<AgentSessionThreadPresentationKey>()
      for ((key, update) in updates) {
        val existing = next[key]
        val presentation = AgentSessionThreadPresentation(
          title = update.first ?: existing?.title.orEmpty(),
          activity = update.second ?: existing?.activity ?: AgentThreadActivity.READY,
        )
        if (existing == presentation) continue
        next[key] = presentation
        changedKeys.add(key)
      }
      PresentationStateUpdate(next = next, changedKeys = changedKeys, removedKeys = emptySet())
    }
  }

  private fun updateState(transform: (Map<AgentSessionThreadPresentationKey, AgentSessionThreadPresentation>) -> PresentationStateUpdate): AgentSessionThreadPresentationChangeSet {
    val changeSet = synchronized(lock) {
      val update = transform(mutableState.value)
      if (update.changedKeys.isEmpty() && update.removedKeys.isEmpty()) {
        return@synchronized AgentSessionThreadPresentationChangeSet.EMPTY
      }
      mutableState.value = update.next
      AgentSessionThreadPresentationChangeSet(
        changedKeys = update.changedKeys,
        removedKeys = update.removedKeys,
      )
    }
    if (!changeSet.isEmpty) {
      mutableChanges.tryEmit(changeSet)
    }
    return changeSet
  }
}

private data class PresentationStateUpdate(
  @JvmField val next: Map<AgentSessionThreadPresentationKey, AgentSessionThreadPresentation>,
  @JvmField val changedKeys: Set<AgentSessionThreadPresentationKey>,
  @JvmField val removedKeys: Set<AgentSessionThreadPresentationKey>,
)
