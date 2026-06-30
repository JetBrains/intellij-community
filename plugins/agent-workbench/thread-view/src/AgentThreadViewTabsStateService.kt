// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.sessions.core.isAgentSessionPendingThreadId
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.TestOnly

private const val AGENT_THREAD_VIEW_TABS_STATE_VERSION = 13
private const val AGENT_THREAD_VIEW_TABS_STATE_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000

private val LOG = logger<AgentThreadViewTabsStateService>()

@Service(Service.Level.APP)
@State(name = "AgentThreadViewTabsState", storages = [Storage(StoragePathMacros.CACHE_FILE)])
internal class AgentThreadViewTabsStateService(@Suppress("UNUSED_PARAMETER") scope: CoroutineScope?) :
  SerializablePersistentStateComponent<AgentThreadViewTabsState>(AgentThreadViewTabsState()) {

  @Volatile
  private var versionMismatchForcedForTests: Boolean = false

  fun load(tabKey: AgentThreadViewTabKey): AgentThreadViewTabSnapshot? {
    if (hasVersionMismatch()) {
      return null
    }
    val entry = state.tabsByKey[tabKey.value] ?: return null
    if (isExpired(entry.updatedAt)) {
      delete(tabKey)
      return null
    }
    return entry.toSnapshot(tabKey)
  }

  fun load(tabKey: String): AgentThreadViewTabSnapshot? {
    return AgentThreadViewTabKey.parse(tabKey)?.let(::load)
  }

  fun upsert(snapshot: AgentThreadViewTabSnapshot) {
    val now = System.currentTimeMillis()
    updateState { current ->
      val updatedTabs = normalizeTabsForWrite(current).toMutableMap()
      updatedTabs.put(snapshot.tabKey.value, snapshot.toPersisted(now))
      current.copy(
        version = AGENT_THREAD_VIEW_TABS_STATE_VERSION,
        tabsByKey = updatedTabs,
      )
    }
  }

  fun delete(tabKey: AgentThreadViewTabKey): Boolean {
    return deleteAndGetSnapshot(tabKey) != null
  }

  fun deleteAndGetSnapshot(tabKey: AgentThreadViewTabKey): AgentThreadViewTabSnapshot? {
    var deletedSnapshot: AgentThreadViewTabSnapshot? = null

    updateState { current ->
      val versionMismatch = hasVersionMismatch(current)
      val baseTabs = normalizeTabsForWrite(current)
      deletedSnapshot = baseTabs[tabKey.value]?.toSnapshot(tabKey)
      if (deletedSnapshot == null && !versionMismatch) {
        return@updateState current
      }

      val updatedTabs = baseTabs.toMutableMap()
      updatedTabs.remove(tabKey.value)
      current.copy(
        version = AGENT_THREAD_VIEW_TABS_STATE_VERSION,
        tabsByKey = updatedTabs,
      )
    }
    return deletedSnapshot
  }

  fun deleteByThread(projectPath: String, threadIdentity: String, subAgentId: String? = null): Int {
    return deleteByThreadWithKeys(projectPath, threadIdentity, subAgentId).deletedKeys.size
  }

  fun deleteByThreadWithKeys(
    projectPath: String,
    threadIdentity: String,
    subAgentId: String? = null,
  ): AgentThreadViewDeleteByThreadResult {
    val normalizedProjectPath = normalizeAgentWorkbenchPath(projectPath)
    var keysToDelete = emptyList<String>()
    var deletedTabs = emptyList<AgentThreadViewTabSnapshot>()

    updateState { current ->
      val versionMismatch = hasVersionMismatch(current)
      val baseTabs = normalizeTabsForWrite(current)
      val tabsToDelete = baseTabs.entries
        .filter { (_, tab) ->
          normalizeAgentWorkbenchPath(tab.projectPath) == normalizedProjectPath &&
          tab.threadIdentity == threadIdentity &&
          (subAgentId == null || tab.subAgentId == subAgentId)
        }
      keysToDelete = tabsToDelete.map { (key, _) -> key }
      deletedTabs = tabsToDelete.mapNotNull { (key, tab) ->
        AgentThreadViewTabKey.parse(key)?.let(tab::toSnapshot)
      }

      if (keysToDelete.isEmpty() && !versionMismatch) {
        return@updateState current
      }

      val updatedTabs = baseTabs.toMutableMap()
      keysToDelete.forEach(updatedTabs::remove)
      current.copy(
        version = AGENT_THREAD_VIEW_TABS_STATE_VERSION,
        tabsByKey = updatedTabs,
      )
    }
    return AgentThreadViewDeleteByThreadResult(
      deletedKeys = keysToDelete,
      deletedTabs = deletedTabs,
    )
  }

  fun pruneStale() {
    if (hasVersionMismatch()) {
      return
    }
    val now = System.currentTimeMillis()
    val filtered = state.tabsByKey.filterValues { tab -> !isExpired(tab.updatedAt, now) }
    if (filtered.size == state.tabsByKey.size && state.version == AGENT_THREAD_VIEW_TABS_STATE_VERSION) {
      return
    }

    updateState {
      AgentThreadViewTabsState(
        version = AGENT_THREAD_VIEW_TABS_STATE_VERSION,
        tabsByKey = filtered,
      )
    }
  }

  fun hasVersionMismatch(): Boolean = hasVersionMismatch(state)

  @TestOnly
  internal fun forceVersionMismatchForTests(value: Boolean) {
    versionMismatchForcedForTests = value
  }

  private fun hasVersionMismatch(current: AgentThreadViewTabsState): Boolean {
    return versionMismatchForcedForTests || current.version != AGENT_THREAD_VIEW_TABS_STATE_VERSION
  }

  private fun normalizeTabsForWrite(current: AgentThreadViewTabsState): Map<String, PersistedAgentThreadViewTabState> {
    return if (hasVersionMismatch(current)) {
      emptyMap()
    }
    else {
      current.tabsByKey
    }
  }
}

@Serializable
internal data class AgentThreadViewTabsState(
  @JvmField val version: Int = AGENT_THREAD_VIEW_TABS_STATE_VERSION,
  @JvmField val tabsByKey: Map<String, PersistedAgentThreadViewTabState> = emptyMap(),
)

internal data class AgentThreadViewDeleteByThreadResult(
  @JvmField val deletedKeys: List<String>,
  @JvmField val deletedTabs: List<AgentThreadViewTabSnapshot>,
)

@Serializable
internal data class PersistedAgentThreadViewTabState(
  @JvmField val projectHash: String,
  @JvmField val projectPath: String,
  @JvmField val projectDirectory: String? = null,
  @JvmField val threadIdentity: String,
  @JvmField val subAgentId: String?,
  @JvmField val threadId: String,
  @JvmField val lastKnownTitle: String,
  @JvmField val lastKnownActivity: String = AgentThreadActivity.READY.name,
  @JvmField val pendingCreatedAtMs: Long? = null,
  @JvmField val pendingFirstInputAtMs: Long? = null,
  @JvmField val pendingLaunchMode: String? = null,
  @JvmField val launchMode: String? = null,
  @JvmField val launchProfileId: String? = null,
  @JvmField val launchTargetId: String? = null,
  @JvmField val surfaceId: String? = null,
  @JvmField val newThreadRebindRequestedAtMs: Long? = null,
  @JvmField val updatedAt: Long,
)

private fun isExpired(updatedAt: Long): Boolean {
  return isExpired(updatedAt, System.currentTimeMillis())
}

private fun isExpired(updatedAt: Long, now: Long): Boolean {
  return updatedAt <= 0 || now - updatedAt > AGENT_THREAD_VIEW_TABS_STATE_TTL_MILLIS
}

private fun PersistedAgentThreadViewTabState.toSnapshot(tabKey: AgentThreadViewTabKey): AgentThreadViewTabSnapshot {
  val resolvedPendingCreatedAtMs = pendingCreatedAtMs
                                   ?: updatedAt.takeIf { it > 0L && isPersistedPendingThreadIdentity(threadIdentity) }
  return AgentThreadViewTabSnapshot(
    tabKey = tabKey,
    identity = AgentThreadViewTabIdentity(
      projectHash = projectHash,
      projectPath = projectPath,
      projectDirectory = projectDirectory?.let(::normalizeAgentWorkbenchPath)?.takeIf { it.isNotBlank() },
      threadIdentity = threadIdentity,
      subAgentId = subAgentId,
    ),
    runtime = AgentThreadViewTabRuntime(
      threadId = threadId,
      threadTitle = lastKnownTitle,
      threadActivity = parseThreadActivity(lastKnownActivity),
      pendingCreatedAtMs = resolvedPendingCreatedAtMs,
      pendingFirstInputAtMs = pendingFirstInputAtMs,
      pendingLaunchMode = pendingLaunchMode,
      launchMode = normalizeAgentThreadViewLaunchMode(launchMode),
      launchProfileId = launchProfileId,
      launchTargetId = launchTargetId,
      surfaceId = normalizeAgentThreadViewSurfaceId(surfaceId),
      newThreadRebindRequestedAtMs = newThreadRebindRequestedAtMs,
      // Prompt text, tokens, delivery state, and dispatch queues are live-session metadata and are intentionally not restored.
      initialPromptRecord = null,
      terminalPromptDispatch = null,
    ),
  )
}

private fun AgentThreadViewTabSnapshot.toPersisted(updatedAt: Long): PersistedAgentThreadViewTabState {
  return PersistedAgentThreadViewTabState(
    projectHash = identity.projectHash,
    projectPath = identity.projectPath,
    projectDirectory = identity.projectDirectory,
    threadIdentity = identity.threadIdentity,
    subAgentId = identity.subAgentId,
    threadId = runtime.threadId,
    lastKnownTitle = runtime.threadTitle,
    lastKnownActivity = runtime.threadActivity.name,
    pendingCreatedAtMs = runtime.pendingCreatedAtMs,
    pendingFirstInputAtMs = runtime.pendingFirstInputAtMs,
    pendingLaunchMode = runtime.pendingLaunchMode,
    launchMode = runtime.launchMode,
    launchProfileId = runtime.launchProfileId,
    launchTargetId = runtime.launchTargetId,
    surfaceId = runtime.surfaceId,
    newThreadRebindRequestedAtMs = runtime.newThreadRebindRequestedAtMs,
    updatedAt = updatedAt,
  )
}

private fun isPersistedPendingThreadIdentity(threadIdentity: String): Boolean {
  val separator = threadIdentity.indexOf(':')
  return separator > 0 && isAgentSessionPendingThreadId(threadIdentity.substring(separator + 1))
}

private fun parseThreadActivity(value: String): AgentThreadActivity {
  return runCatching { AgentThreadActivity.valueOf(value) }
    .getOrDefault(AgentThreadActivity.READY)
}
