// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class AgentSessionRebindCandidate(
  @JvmField val threadId: String,
  @JvmField val title: String,
  @JvmField val updatedAt: Long,
  @JvmField val activity: AgentThreadActivity,
)

data class AgentSessionThreadActivityUpdate(
  @JvmField val activityReport: AgentThreadActivityReport,
  @JvmField val updatesChromeActivity: Boolean = true,
  @JvmField val updatedAt: Long? = null,
)

data class AgentSessionThreadPresentationUpdate(
  @JvmField val title: String? = null,
  @JvmField val activityReport: AgentThreadActivityReport? = null,
  @JvmField val updatesChromeActivity: Boolean = true,
  @JvmField val updatedAt: Long? = null,
)

data class AgentSessionRefreshHints(
  @JvmField val rebindCandidates: List<AgentSessionRebindCandidate> = emptyList(),
  @JvmField val activityUpdatesByThreadId: Map<String, AgentSessionThreadActivityUpdate> = emptyMap(),
  @JvmField val presentationUpdatesByThreadId: Map<String, AgentSessionThreadPresentationUpdate> = emptyMap(),
)

const val UNKNOWN_AGENT_SESSION_REFRESH_THREAD_UPDATED_AT: Long = -1L

data class AgentSessionRefreshThreadSeed(
  @JvmField val threadId: String,
  @JvmField val updatedAt: Long = UNKNOWN_AGENT_SESSION_REFRESH_THREAD_UPDATED_AT,
  @JvmField val forceRefresh: Boolean = false,
)

fun Collection<String>.toAgentSessionRefreshThreadSeeds(): Set<AgentSessionRefreshThreadSeed> {
  return asSequence()
    .map { threadId -> AgentSessionRefreshThreadSeed(threadId = threadId) }
    .toCollection(LinkedHashSet())
}

enum class AgentSessionSourceUpdate {
  THREADS_CHANGED,
  HINTS_CHANGED,
}

data class AgentSessionSourceUpdateEvent(
  @JvmField val type: AgentSessionSourceUpdate,
  @JvmField val scopedPaths: Set<String>? = null,
  @JvmField val threadIds: Set<String>? = null,
  @JvmField val activityUpdatesByThreadId: Map<String, AgentSessionThreadActivityUpdate> = emptyMap(),
  @JvmField val presentationUpdatesByThreadId: Map<String, AgentSessionThreadPresentationUpdate> = emptyMap(),
  @JvmField val mayHaveChangedProjectFiles: Boolean = false,
  @JvmField val changedProjectFilePaths: Set<String>? = null,
)

fun AgentSessionThreadActivityUpdate.toPresentationUpdate(): AgentSessionThreadPresentationUpdate {
  return AgentSessionThreadPresentationUpdate(
    activityReport = activityReport,
    updatesChromeActivity = updatesChromeActivity,
    updatedAt = updatedAt,
  )
}

fun mergeAgentSessionThreadPresentationUpdates(
  existing: AgentSessionThreadPresentationUpdate,
  incoming: AgentSessionThreadPresentationUpdate,
): AgentSessionThreadPresentationUpdate {
  val existingUpdatedAt = existing.updatedAt
  val incomingUpdatedAt = incoming.updatedAt
  if (existingUpdatedAt != null && incomingUpdatedAt != null && incomingUpdatedAt < existingUpdatedAt) {
    return existing
  }
  val updatedAt = when {
    existingUpdatedAt == null -> incomingUpdatedAt
    incomingUpdatedAt == null -> existingUpdatedAt
    else -> maxOf(existingUpdatedAt, incomingUpdatedAt)
  }
  val updatesChromeActivity = incoming.updatesChromeActivity || existing.updatesChromeActivity
  val activityReport = when {
    incoming.activityReport == null -> existing.activityReport
    existing.activityReport == null -> incoming.activityReport
    else -> incoming.activityReport.copy(
      chromeActivity = if (incoming.updatesChromeActivity) incoming.activityReport.chromeActivity else existing.activityReport.chromeActivity,
    )
  }
  return AgentSessionThreadPresentationUpdate(
    title = incoming.title ?: existing.title,
    activityReport = activityReport,
    updatesChromeActivity = updatesChromeActivity,
    updatedAt = updatedAt,
  )
}

data class AgentSessionSourceRefreshRequest(
  @JvmField val paths: List<String>,
  @JvmField val threadIds: Set<String> = emptySet(),
  @JvmField val updateEvent: AgentSessionSourceUpdateEvent,
) {
  val isThreadScoped: Boolean
    get() = threadIds.isNotEmpty()
}

data class AgentSessionSourceRefreshResult(
  @JvmField val completeThreadsByPath: Map<String, List<AgentSessionThread>> = emptyMap(),
  @JvmField val partialThreadsByPath: Map<String, List<AgentSessionThread>> = emptyMap(),
  @JvmField val removedThreadIdsByPath: Map<String, Set<String>> = emptyMap(),
  @JvmField val failuresByPath: Map<String, Throwable> = emptyMap(),
)

fun AgentSessionSourceUpdateEvent.isUnscoped(): Boolean {
  return scopedPaths == null && threadIds == null
}

fun AgentSessionSourceUpdateEvent.describeScope(): String {
  val scopedPaths = scopedPaths
  val threadIds = threadIds
  val scope = when {
    scopedPaths == null && threadIds == null -> "scope=all"
    scopedPaths != null && threadIds != null -> "scope=paths:${scopedPaths.size},threadIds:${threadIds.size}"
    scopedPaths != null -> "scope=paths:${scopedPaths.size}"
    else -> "scope=threadIds:${threadIds?.size ?: 0}"
  }
  return changedProjectFilePaths?.let { paths -> "$scope,changedProjectFiles:${paths.size}" } ?: scope
}

interface AgentSessionSource {
  val provider: AgentSessionProvider
  val canReportExactThreadCount: Boolean
    get() = true

  val supportsUpdates: Boolean
    get() = false

  val supportsArchivedThreads: Boolean
    get() = false

  /**
   * Typed source updates used by the loading coordinator to distinguish
   * backend listing updates from auxiliary hint updates.
   */
  val updateEvents: Flow<AgentSessionSourceUpdateEvent>
    get() = emptyFlow()

  /**
   * Provider-filtered updates for an actively running thread.
   *
   * Implementations should parse raw file notifications and emit only meaningful source updates,
   * such as activity changes or project-file change evidence. Unchanged persistence writes should
   * not be surfaced as refresh signals.
   */
  fun activeThreadUpdateEvents(path: String, threadId: String): Flow<AgentSessionSourceUpdateEvent> = emptyFlow()

  suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread>

  suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread>

  suspend fun listArchivedThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> {
    return listArchivedThreads(path = path, openProject = project)
  }

  suspend fun listArchivedThreadsFromClosedProject(path: String): List<AgentSessionThread> {
    return listArchivedThreads(path = path, openProject = null)
  }

  suspend fun listArchivedThreads(path: String, openProject: Project?): List<AgentSessionThread> = emptyList()

  /**
   * Refreshes provider rows for [AgentSessionSourceRefreshRequest.paths].
   *
   * [AgentSessionSourceRefreshResult.completeThreadsByPath] is authoritative for a path and replaces all provider rows there.
   * [AgentSessionSourceRefreshResult.partialThreadsByPath] updates only the returned thread ids and must not evict other rows.
   * [AgentSessionSourceRefreshResult.failuresByPath] reports path-local failures; other paths in the same request may still succeed.
   */
  suspend fun refreshThreads(request: AgentSessionSourceRefreshRequest): AgentSessionSourceRefreshResult {
    if (request.paths.isEmpty()) {
      return AgentSessionSourceRefreshResult()
    }

    val prefetched = try {
      prefetchThreads(request.paths)
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      emptyMap()
    }
    val completeThreadsByPath = LinkedHashMap<String, List<AgentSessionThread>>(request.paths.size)
    val failuresByPath = LinkedHashMap<String, Throwable>()
    for (path in request.paths) {
      val prefetchedThreads = prefetched[path]
      if (prefetchedThreads != null) {
        completeThreadsByPath[path] = prefetchedThreads
        continue
      }
      try {
        completeThreadsByPath[path] = listThreadsFromClosedProject(path)
      }
      catch (e: Throwable) {
        if (e is CancellationException) throw e
        failuresByPath[path] = e
      }
    }
    return AgentSessionSourceRefreshResult(
      completeThreadsByPath = completeThreadsByPath,
      failuresByPath = failuresByPath,
    )
  }

  /**
   * Prefetch threads for multiple paths in a single backend call.
   * Returns a map of path to threads. Empty map means no prefetch (use per-path calls).
   */
  suspend fun prefetchThreads(paths: List<String>): Map<String, List<AgentSessionThread>> = emptyMap()

  /**
   * Optional provider-specific refresh hints used by the loading coordinator.
   *
   * Hints must not add persisted rows directly. They are consumed for
   * pending-tab rebinding and provider-specific status projection.
   */
  suspend fun prefetchRefreshHints(
    paths: List<String>,
    refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
  ): Map<String, AgentSessionRefreshHints> = emptyMap()

  suspend fun loadThreadCosts(
    path: String,
    threads: List<AgentSessionThread>,
  ): Map<String, AgentSessionCost?> = emptyMap()

  fun markThreadAsRead(threadId: String, updatedAt: Long) {}

  fun setActiveThreadId(threadId: String?) {}
}
