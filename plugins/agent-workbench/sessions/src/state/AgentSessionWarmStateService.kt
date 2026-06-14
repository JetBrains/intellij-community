// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.state

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.session.AgentSessionCostKind
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.session.AgentSubAgent
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.model.isTerminal
import com.intellij.agent.workbench.sessions.model.sortAgentSessionThreadsForDisplay
import com.intellij.agent.workbench.sessions.tree.threadDisplayTitle
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import kotlinx.serialization.Serializable
import java.math.BigDecimal

internal interface SessionWarmState {
  fun getPathSnapshot(path: String): AgentSessionWarmPathSnapshot?

  fun setPathSnapshot(path: String, snapshot: AgentSessionWarmPathSnapshot): Boolean

  fun removePathSnapshot(path: String): Boolean

  fun retainPathSnapshots(paths: Set<String>): Boolean
}

internal data class AgentSessionWarmPathSnapshot(
  @JvmField val threads: List<AgentSessionThread>,
  @JvmField val providerLoadStates: Map<AgentSessionProvider, AgentSessionProviderLoadState> = emptyMap(),
  @JvmField val providersWithUnknownThreadCount: Set<AgentSessionProvider> = emptySet(),
  @JvmField val updatedAt: Long,
) {
  val hasUnknownThreadCount: Boolean
    get() = providersWithUnknownThreadCount.isNotEmpty()
}

internal class InMemorySessionWarmState : SessionWarmState {
  private val snapshotsByPath = LinkedHashMap<String, AgentSessionWarmPathSnapshot>()

  override fun getPathSnapshot(path: String): AgentSessionWarmPathSnapshot? {
    return snapshotsByPath[normalizeAgentWorkbenchPath(path)]
  }

  override fun setPathSnapshot(path: String, snapshot: AgentSessionWarmPathSnapshot): Boolean {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val normalizedSnapshot = normalizeWarmPathSnapshot(snapshot)
    val current = snapshotsByPath[normalizedPath]
    if (current == normalizedSnapshot) {
      return false
    }
    snapshotsByPath[normalizedPath] = normalizedSnapshot
    return true
  }

  override fun removePathSnapshot(path: String): Boolean {
    return snapshotsByPath.remove(normalizeAgentWorkbenchPath(path)) != null
  }

  override fun retainPathSnapshots(paths: Set<String>): Boolean {
    val normalizedPaths = paths.mapTo(LinkedHashSet()) { normalizeAgentWorkbenchPath(it) }
    var changed = false
    val iterator = snapshotsByPath.keys.iterator()
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
@State(name = "AgentSessionWarmState", storages = [Storage(StoragePathMacros.CACHE_FILE)])
internal class AgentSessionWarmStateService
  : SerializablePersistentStateComponent<AgentSessionWarmStateService.WarmState>(WarmState()),
    SessionWarmState {

  override fun getPathSnapshot(path: String): AgentSessionWarmPathSnapshot? {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    return state.snapshotsByPath[normalizedPath]?.toSnapshot()
  }

  override fun setPathSnapshot(path: String, snapshot: AgentSessionWarmPathSnapshot): Boolean {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    val normalizedSnapshot = normalizeWarmPathSnapshot(snapshot).toState()
    val current = state.snapshotsByPath[normalizedPath]
    if (current == normalizedSnapshot) {
      return false
    }
    updateState { currentState ->
      val updated = currentState.snapshotsByPath.toMutableMap()
      updated[normalizedPath] = normalizedSnapshot
      currentState.copy(snapshotsByPath = updated)
    }
    return true
  }

  override fun removePathSnapshot(path: String): Boolean {
    val normalizedPath = normalizeAgentWorkbenchPath(path)
    if (state.snapshotsByPath[normalizedPath] == null) {
      return false
    }
    updateState { currentState ->
      val updated = currentState.snapshotsByPath.toMutableMap()
      updated.remove(normalizedPath)
      currentState.copy(snapshotsByPath = updated)
    }
    return true
  }

  override fun retainPathSnapshots(paths: Set<String>): Boolean {
    val normalizedPaths = paths.mapTo(LinkedHashSet()) { normalizeAgentWorkbenchPath(it) }
    val current = state.snapshotsByPath
    if (current.keys.all { it in normalizedPaths }) {
      return false
    }
    updateState { currentState ->
      val filtered = currentState.snapshotsByPath.filterKeys { it in normalizedPaths }
      currentState.copy(snapshotsByPath = filtered)
    }
    return true
  }

  @Serializable
  internal data class WarmState(
    @JvmField val snapshotsByPath: Map<String, WarmPathSnapshotState> = emptyMap(),
  )

  @Serializable
  internal data class WarmPathSnapshotState(
    @JvmField val threads: List<WarmThreadState> = emptyList(),
    @JvmField val providerLoadStates: Map<String, String> = emptyMap(),
    @JvmField val providersWithUnknownThreadCount: List<String> = emptyList(),
    @JvmField val updatedAt: Long = 0,
  )

  @Serializable
  internal data class WarmThreadState(
    @JvmField val id: String,
    @JvmField val title: String,
    @JvmField val updatedAt: Long,
    @JvmField val activity: String,
    @JvmField val provider: String,
    @JvmField val subAgents: List<WarmSubAgentState> = emptyList(),
    @JvmField val originBranch: String? = null,
    @JvmField val summaryActivity: String? = activity,
    @JvmField val cost: WarmThreadCostState? = null,
  )

  @Serializable
  internal data class WarmThreadCostState(
    @JvmField val amountUsd: String? = null,
    @JvmField val kind: String = AgentSessionCostKind.UNAVAILABLE.name,
    @JvmField val matchedModelId: String? = null,
  )

  @Serializable
  internal data class WarmSubAgentState(
    @JvmField val id: String,
    @JvmField val name: String,
    @JvmField val activity: String = AgentThreadActivity.READY.name,
  )
}

private fun normalizeWarmPathSnapshot(snapshot: AgentSessionWarmPathSnapshot): AgentSessionWarmPathSnapshot {
  val terminalProviderLoadStates = snapshot.providerLoadStates.filterValues { state -> state.isTerminal }
  return snapshot.copy(
    threads = snapshot.threads
      .asSequence()
      .filterNot { it.archived || isAgentSessionNewSessionId(it.id) }
      .map { thread ->
        thread.copy(
          archived = false,
          title = threadDisplayTitle(threadId = thread.id, title = thread.title),
        )
      }
      .toList()
      .let(::sortAgentSessionThreadsForDisplay),
    providerLoadStates = terminalProviderLoadStates,
    providersWithUnknownThreadCount = snapshot.providersWithUnknownThreadCount.filterTo(LinkedHashSet()) { provider ->
      terminalProviderLoadStates[provider] == AgentSessionProviderLoadState.LOADED
    },
  )
}

private fun AgentSessionWarmStateService.WarmPathSnapshotState.toSnapshot(): AgentSessionWarmPathSnapshot {
  val parsedProviderLoadStates = providerLoadStates.toProviderLoadStates()
  return AgentSessionWarmPathSnapshot(
    threads = threads.mapNotNull { thread ->
      val provider = AgentSessionProvider.fromOrNull(thread.provider) ?: return@mapNotNull null
      AgentSessionThread(
        id = thread.id,
        title = threadDisplayTitle(threadId = thread.id, title = thread.title),
        updatedAt = thread.updatedAt,
        archived = false,
        activity = parseWarmStateThreadActivity(thread.activity),
        provider = provider,
        subAgents = thread.subAgents.map { subAgent ->
          AgentSubAgent(
            id = subAgent.id,
            name = subAgent.name,
            activity = parseWarmStateThreadActivity(subAgent.activity),
          )
        },
        originBranch = thread.originBranch,
        summaryActivity = parseWarmStateThreadSummaryActivity(thread.summaryActivity),
        cost = thread.cost?.toCost(),
      )
    },
    providerLoadStates = parsedProviderLoadStates,
    providersWithUnknownThreadCount = providersWithUnknownThreadCount.mapNotNullTo(LinkedHashSet()) { providerId ->
      AgentSessionProvider.fromOrNull(providerId)
    },
    updatedAt = updatedAt,
  )
}

private fun AgentSessionWarmPathSnapshot.toState(): AgentSessionWarmStateService.WarmPathSnapshotState {
  return AgentSessionWarmStateService.WarmPathSnapshotState(
    threads = threads.map { thread ->
      AgentSessionWarmStateService.WarmThreadState(
        id = thread.id,
        title = thread.title,
        updatedAt = thread.updatedAt,
        activity = thread.activity.name,
        provider = thread.provider.value,
        subAgents = thread.subAgents.map { subAgent ->
          AgentSessionWarmStateService.WarmSubAgentState(id = subAgent.id, name = subAgent.name, activity = subAgent.activity.name)
        },
        originBranch = thread.originBranch,
        summaryActivity = thread.summaryActivity?.name,
        cost = thread.cost?.toState(),
      )
    },
    providerLoadStates = providerLoadStates.mapKeys { (provider, _) -> provider.value }.mapValues { (_, state) -> state.name },
    providersWithUnknownThreadCount = providersWithUnknownThreadCount.map { provider -> provider.value },
    updatedAt = updatedAt,
  )
}

private fun Map<String, String>.toProviderLoadStates(): Map<AgentSessionProvider, AgentSessionProviderLoadState> {
  if (isEmpty()) {
    return emptyMap()
  }
  return entries.mapNotNull { (providerId, stateName) ->
    val provider = AgentSessionProvider.fromOrNull(providerId) ?: return@mapNotNull null
    val state = runCatching { AgentSessionProviderLoadState.valueOf(stateName) }.getOrNull() ?: return@mapNotNull null
    provider to state
  }.toMap()
}

private fun parseWarmStateThreadActivity(value: String): AgentThreadActivity {
  return runCatching { AgentThreadActivity.valueOf(value) }
    .getOrDefault(AgentThreadActivity.READY)
}

private fun parseWarmStateThreadSummaryActivity(value: String?): AgentThreadActivity? {
  return value?.let { runCatching { AgentThreadActivity.valueOf(it) }.getOrNull() }
}

private fun AgentSessionWarmStateService.WarmThreadCostState.toCost(): AgentSessionCost? {
  val kind = runCatching { AgentSessionCostKind.valueOf(kind) }.getOrNull() ?: return null
  val amountUsd = amountUsd?.let { value -> runCatching { BigDecimal(value) }.getOrNull() }
  return AgentSessionCost(
    amountUsd = amountUsd,
    kind = kind,
    matchedModelId = matchedModelId,
  )
}

private fun AgentSessionCost.toState(): AgentSessionWarmStateService.WarmThreadCostState {
  return AgentSessionWarmStateService.WarmThreadCostState(
    amountUsd = amountUsd?.toPlainString(),
    kind = kind.name,
    matchedModelId = matchedModelId,
  )
}
