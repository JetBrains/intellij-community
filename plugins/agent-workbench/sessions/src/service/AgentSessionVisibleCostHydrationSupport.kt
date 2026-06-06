// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.isWorking
import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.AgentSessionCostPresentationSettings
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.normalizeConcreteAgentSessionThreadId
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.state.AgentSessionsStateStore
import com.intellij.agent.workbench.sessions.state.DEFAULT_VISIBLE_THREAD_COUNT
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<AgentSessionVisibleCostHydrationSupport>()
private val VISIBLE_COST_HYDRATION_DELAY = 750.milliseconds

internal class AgentSessionVisibleCostHydrationSupport(
  private val serviceScope: CoroutineScope,
  private val stateStore: AgentSessionsStateStore,
  private val contentRepository: AgentSessionContentRepository,
  private val sessionSourcesProvider: () -> List<AgentSessionSource>,
  private val toolWindowVisibleFlow: StateFlow<Boolean>,
  private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
  private val costCache = ConcurrentHashMap<ThreadCacheKey, ThreadCostCacheEntry>()
  private val inFlightLoads = ConcurrentHashMap.newKeySet<ThreadLoadKey>()

  fun start() {
    serviceScope.launch(Dispatchers.Default) {
      combine(stateStore.state, toolWindowVisibleFlow, AgentSessionCostPresentationSettings.enabledFlow) { state, toolWindowVisible, costEnabled ->
        VisibleCostHydrationSnapshot(
          state = state,
          toolWindowVisible = toolWindowVisible,
          costEnabled = costEnabled,
        )
      }.collectLatest { snapshot ->
        if (!snapshot.toolWindowVisible || !snapshot.costEnabled) {
          return@collectLatest
        }
        delay(VISIBLE_COST_HYDRATION_DELAY)
        hydrateVisibleThreadCosts(snapshot.state)
      }
    }
  }

  private fun hydrateVisibleThreadCosts(state: AgentSessionsState) {
    val visibleThreads = collectVisibleThreads(state)
    if (visibleThreads.isEmpty()) {
      return
    }

    val now = currentTimeMillis()
    val sourcesByProvider = sessionSourcesProvider().associateBy { source -> source.provider }
    val cachedUpdatesByPath = LinkedHashMap<String, MutableMap<AgentSessionProvider, MutableMap<String, ThreadCostUpdate>>>()
    val loadRequests = LinkedHashMap<Pair<AgentSessionSource, String>, MutableList<VisibleThreadSnapshot>>()

    for (visibleThread in visibleThreads) {
      if (normalizeConcreteAgentSessionThreadId(visibleThread.threadId) == null) {
        continue
      }
      if (visibleThread.activity.isWorking) {
        continue
      }

      val cacheKey = visibleThread.cacheKey
      val cacheEntry = costCache[cacheKey]

      if (visibleThread.cost != null && (cacheEntry == null || cacheEntry.updatedAt != visibleThread.updatedAt)) {
        costCache[cacheKey] = ThreadCostCacheEntry(
          updatedAt = visibleThread.updatedAt,
          refreshedAtMs = now,
          cost = visibleThread.cost,
        )
      }

      val effectiveCacheEntry = costCache[cacheKey]
      if (effectiveCacheEntry != null && effectiveCacheEntry.updatedAt == visibleThread.updatedAt) {
        if (visibleThread.cost != effectiveCacheEntry.cost) {
          cachedUpdatesByPath
            .getOrPut(visibleThread.path) { LinkedHashMap() }
            .getOrPut(visibleThread.provider) { LinkedHashMap() }[visibleThread.threadId] = ThreadCostUpdate(
            expectedUpdatedAt = visibleThread.updatedAt,
            cost = effectiveCacheEntry.cost,
          )
        }
        continue
      }

      val source = sourcesByProvider[visibleThread.provider] ?: continue
      val loadKey = visibleThread.loadKey
      if (!inFlightLoads.add(loadKey)) {
        continue
      }
      loadRequests.getOrPut(source to visibleThread.path) { ArrayList() }.add(visibleThread)
    }

    applyThreadCostUpdates(cachedUpdatesByPath)

    for ((requestKey, requestedThreads) in loadRequests) {
      val (source, path) = requestKey
      serviceScope.launch(Dispatchers.IO) {
        try {
          val requestedAgentThreads = requestedThreads.map { visibleThread -> visibleThread.thread }
          val loadedCostsByThreadId = source.loadThreadCosts(path = path, threads = requestedAgentThreads)
          val refreshedAt = currentTimeMillis()
          val updatesByProvider = LinkedHashMap<AgentSessionProvider, MutableMap<String, ThreadCostUpdate>>()
          for (visibleThread in requestedThreads) {
            val loadedCost = loadedCostsByThreadId[visibleThread.threadId]
            costCache[visibleThread.cacheKey] = ThreadCostCacheEntry(
              updatedAt = visibleThread.updatedAt,
              refreshedAtMs = refreshedAt,
              cost = loadedCost,
            )
            updatesByProvider
              .getOrPut(visibleThread.provider) { LinkedHashMap() }[visibleThread.threadId] = ThreadCostUpdate(
              expectedUpdatedAt = visibleThread.updatedAt,
              cost = loadedCost,
            )
          }
          if (toolWindowVisibleFlow.value && AgentSessionCostPresentationSettings.isEnabled()) {
            applyThreadCostUpdates(mapOf(path to updatesByProvider))
          }
        }
        catch (t: Throwable) {
          LOG.debug(t) { "Failed to hydrate visible thread costs for ${source.provider.value} path=$path threads=${requestedThreads.size}" }
        }
        finally {
          requestedThreads.forEach { visibleThread ->
            inFlightLoads.remove(visibleThread.loadKey)
          }
        }
      }
    }
  }

  private fun applyThreadCostUpdates(
    updatesByPath: Map<String, Map<AgentSessionProvider, Map<String, ThreadCostUpdate>>>,
  ) {
    for ((path, providerUpdates) in updatesByPath) {
      for ((provider, costUpdatesByThreadId) in providerUpdates) {
        if (costUpdatesByThreadId.isEmpty()) {
          continue
        }
        contentRepository.updateThreadCosts(
          path = path,
          provider = provider,
          costUpdatesByThreadId = costUpdatesByThreadId,
        )
      }
    }
  }

  @Suppress("DuplicatedCode")
  private fun collectVisibleThreads(state: AgentSessionsState): List<VisibleThreadSnapshot> {
    val visibleThreads = ArrayList<VisibleThreadSnapshot>()
    state.projects.forEach { project ->
      collectVisibleThreadsForPath(
        path = project.path,
        threads = project.threads,
        visibleThreadCounts = state.visibleThreadCounts,
        collector = visibleThreads,
      )
      project.worktrees.forEach { worktree ->
        collectVisibleThreadsForPath(
          path = worktree.path,
          threads = worktree.threads,
          visibleThreadCounts = state.visibleThreadCounts,
          collector = visibleThreads,
        )
      }
    }
    return visibleThreads
  }

  private fun collectVisibleThreadsForPath(
    path: String,
    threads: List<AgentSessionThread>,
    visibleThreadCounts: Map<String, Int>,
    collector: MutableList<VisibleThreadSnapshot>,
  ) {
    val visibleCount = visibleThreadCounts[path] ?: DEFAULT_VISIBLE_THREAD_COUNT
    threads.asSequence()
      .take(visibleCount)
      .forEach { thread ->
        collector.add(
          VisibleThreadSnapshot(
            path = path,
            provider = thread.provider,
            thread = thread,
          )
        )
      }
  }
}

internal data class ThreadCostUpdate(
  @JvmField val expectedUpdatedAt: Long,
  @JvmField val cost: AgentSessionCost?,
)

private data class VisibleThreadSnapshot(
  @JvmField val path: String,
  val provider: AgentSessionProvider,
  @JvmField val thread: AgentSessionThread,
) {
  val threadId: String
    get() = thread.id

  val updatedAt: Long
    get() = thread.updatedAt

  val cost: AgentSessionCost?
    get() = thread.cost

  val activity: AgentThreadActivity
    get() = thread.activity

  val cacheKey: ThreadCacheKey
    get() = ThreadCacheKey(path = path, provider = provider, threadId = threadId)

  val loadKey: ThreadLoadKey
    get() = ThreadLoadKey(path = path, provider = provider, threadId = threadId, updatedAt = updatedAt)
}

private data class ThreadCacheKey(
  @JvmField val path: String,
  val provider: AgentSessionProvider,
  @JvmField val threadId: String,
)

private data class ThreadLoadKey(
  @JvmField val path: String,
  val provider: AgentSessionProvider,
  @JvmField val threadId: String,
  @JvmField val updatedAt: Long,
)

private data class ThreadCostCacheEntry(
  @JvmField val updatedAt: Long,
  @JvmField val refreshedAtMs: Long,
  @JvmField val cost: AgentSessionCost?,
)

private data class VisibleCostHydrationSnapshot(
  @JvmField val state: AgentSessionsState,
  @JvmField val toolWindowVisible: Boolean,
  @JvmField val costEnabled: Boolean,
)
