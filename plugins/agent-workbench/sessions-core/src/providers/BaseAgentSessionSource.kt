// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.ConcurrentHashMap

abstract class BaseAgentSessionSource(
  final override val provider: AgentSessionProvider,
  final override val canReportExactThreadCount: Boolean = true,
) : AgentSessionSource {
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
        AgentSessionSourceUpdateEvent(
          type = AgentSessionSourceUpdate.HINTS_CHANGED,
          threadIds = setOf(threadId),
        )
      )
    }
  }

  /**
   * If any thread in [threads] matches [activeThreadId] and [shouldRemember] accepts it,
   * merge its updatedAt into [readTracker]. Stops at first matching id.
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

  final override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> {
    return listThreads(path = path, openProject = project)
  }

  final override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> {
    return listThreads(path = path, openProject = null)
  }

  override suspend fun refreshThreads(request: AgentSessionSourceRefreshRequest): AgentSessionSourceRefreshResult {
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
        completeThreadsByPath[path] = listThreads(path = path, openProject = null)
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

  protected abstract suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread>
}

fun resolveReadTrackedActivity(readTracker: Map<String, Long>, threadId: String, updatedAt: Long): AgentThreadActivity {
  val lastSeenAt = readTracker[threadId] ?: return AgentThreadActivity.READY
  return if (updatedAt > lastSeenAt) AgentThreadActivity.UNREAD else AgentThreadActivity.READY
}
