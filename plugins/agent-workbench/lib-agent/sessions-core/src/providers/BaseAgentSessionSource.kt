// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.sessions.core.providers

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Default base class for provider sources that store read/unread state in Agent Workbench.
 *
 * Subclasses implement [loadThreads] for active thread discovery and may add focused capabilities such as [AgentSessionArchivedSource],
 * [AgentSessionRefreshSource], or [AgentSessionThreadOutlineSource] on the same concrete source. The base class keeps
 * [AgentSessionSource.listThreads] final so shared services always pass through one read-state-aware entry point.
 */
@ApiStatus.Internal
abstract class BaseAgentSessionSource(
  final override val provider: AgentSessionProvider,
  final override val canReportExactThreadCount: Boolean = true,
) : AgentSessionSource, AgentSessionReadStateSource {
  /**
   * Tracks the last-seen `updatedAt` for threads the user has opened.
   * Absent key = never opened -> READY (not UNREAD).
   * Present key = opened at least once; if `thread.updatedAt > storedValue` -> UNREAD.
   */
  protected val readTracker: ConcurrentHashMap<String, Long> = ConcurrentHashMap()

  private val readStateUpdates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(extraBufferCapacity = 1)

  protected val readStateUpdateEvents: Flow<AgentSessionSourceUpdateEvent> = readStateUpdates

  @Volatile
  protected var activeThreadId: String? = null
    private set

  final override fun setActiveThreadId(threadId: String?) {
    activeThreadId = threadId
  }

  final override fun markThreadAsRead(threadId: String, updatedAt: Long) {
    var advanced = false
    readTracker.compute(threadId) { _, current ->
      if (current == null || updatedAt > current) {
        advanced = true
        updatedAt
      }
      else {
        current
      }
    }
    if (advanced) {
      readStateUpdates.tryEmit(
        AgentSessionSourceUpdateEvent.hintsChanged(
          threadIds = setOf(threadId),
        )
      )
    }
  }

  /**
   * Records the currently active thread as read when it is present in [threads].
   *
   * This helper is intended for provider parsers that already have a fresh active-thread snapshot. It stops at the first matching id and
   * merges the row `updatedAt` into [readTracker] only when [shouldRemember] accepts the row.
   */
  protected inline fun <T> rememberActiveThreadRead(
    threads: Iterable<T>,
    id: (T) -> String,
    updatedAt: (T) -> Long,
    shouldRemember: (T) -> Boolean = { true },
  ) {
    val currentActiveId = activeThreadId ?: return
    for (thread in threads) {
      if (id(thread) == currentActiveId) {
        if (shouldRemember(thread)) {
          readTracker.merge(currentActiveId, updatedAt(thread), ::maxOf)
        }
        return
      }
    }
  }

  final override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    return loadThreads(path = path, openProject = openProject)
  }

  /**
   * Loads active, non-archived threads for [path].
   *
   * Implementations must follow the same row contract as [AgentSessionSource.listThreads]. [openProject] is non-null only for an open IDE
   * project whose normalized path is [path]; closed project and worktree discovery must not require it.
   */
  protected abstract suspend fun loadThreads(path: String, openProject: Project?): List<AgentSessionThread>

  /**
   * Fallback [AgentSessionRefreshSource.refreshThreads] implementation based on normal active-thread listing.
   *
   * Providers with only path-level discovery can delegate to this method from their refresh capability. It prefetches first when the same
   * source also implements [AgentSessionPrefetchSource], then returns complete path snapshots and path-local failures.
   */
  protected suspend fun refreshThreadsByListing(request: AgentSessionSourceRefreshRequest): AgentSessionSourceRefreshResult {
    if (request.paths.isEmpty()) {
      return AgentSessionSourceRefreshResult()
    }

    val prefetched = try {
      (this as? AgentSessionPrefetchSource)?.prefetchThreads(request.sourcePaths()).orEmpty()
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      emptyMap()
    }
    val completeThreadsByPath = LinkedHashMap<String, List<AgentSessionThread>>(request.paths.size)
    val failuresByPath = LinkedHashMap<String, Throwable>()
    for (path in request.paths) {
      val sourcePath = request.sourcePathFor(path)
      val prefetchedThreads = prefetched[sourcePath]
      if (prefetchedThreads != null) {
        completeThreadsByPath[path] = prefetchedThreads
        continue
      }
      try {
        completeThreadsByPath[path] = listThreads(path = sourcePath, openProject = null)
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
}

@ApiStatus.Internal
fun resolveReadTrackedActivity(readTracker: Map<String, Long>, threadId: String, updatedAt: Long): AgentThreadActivity {
  val lastSeenAt = readTracker[threadId] ?: return AgentThreadActivity.READY
  return if (updatedAt > lastSeenAt) AgentThreadActivity.UNREAD else AgentThreadActivity.READY
}
