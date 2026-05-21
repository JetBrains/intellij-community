// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.ClaudeSessionActivity
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.BaseAgentSessionSource
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.util.concurrent.ConcurrentHashMap

class ClaudeSessionSource(
  private val backend: ClaudeSessionBackend = createDefaultClaudeSessionBackend(),
) : BaseAgentSessionSource(provider = AgentSessionProvider.CLAUDE) {
  /**
   * Tracks the last-seen `updatedAt` for threads the user has opened.
   * Absent key = never opened → READY (not UNREAD).
   * Present key = opened at least once; if `thread.updatedAt > storedValue` → UNREAD.
   */
  private val readTracker = ConcurrentHashMap<String, Long>()
  private val readStateUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  @Volatile private var activeThreadId: String? = null

  override val supportsUpdates: Boolean get() = true

  override val updateEvents: Flow<AgentSessionSourceUpdateEvent>
    get() = merge(
      backend.updates.map { AgentSessionSourceUpdateEvent(type = AgentSessionSourceUpdate.THREADS_CHANGED) },
      readStateUpdates.map { AgentSessionSourceUpdateEvent(type = AgentSessionSourceUpdate.HINTS_CHANGED) },
    )

  override fun setActiveThreadId(threadId: String?) {
    activeThreadId = threadId
  }

  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    val threads = backend.listThreads(path = path, openProject = openProject)
    val visibleThreads = threads.filterNot(ClaudeBackendThread::archived)
    val currentActiveId = activeThreadId
    if (currentActiveId != null) {
      for (thread in visibleThreads) {
        if (thread.id == currentActiveId) {
          readTracker.merge(thread.id, thread.updatedAt, ::maxOf)
          break
        }
      }
    }
    return visibleThreads.map { it.toAgentSessionThread(readTracker) }
  }

  override suspend fun prefetchRefreshHints(
    paths: List<String>,
    refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
  ): Map<String, AgentSessionRefreshHints> {
    if (paths.isEmpty()) return emptyMap()

    val result = LinkedHashMap<String, AgentSessionRefreshHints>(paths.size)
    for (path in paths) {
      val threads = try {
        backend.listThreads(path = path, openProject = null)
      }
      catch (_: Throwable) {
        continue
      }
      val visibleThreads = threads.filterNot(ClaudeBackendThread::archived)
      val knownIds = refreshThreadSeedsByPath[path].orEmpty().asSequence().map { it.threadId }.toCollection(LinkedHashSet())
      val rebindCandidates = visibleThreads
        .filter { it.id !in knownIds }
        .map { thread ->
          AgentSessionRebindCandidate(
            threadId = thread.id,
            title = thread.title,
            updatedAt = thread.updatedAt,
            activity = thread.effectiveActivity(readTracker),
          )
        }
      if (rebindCandidates.isNotEmpty()) {
        result[path] = AgentSessionRefreshHints(rebindCandidates = rebindCandidates)
      }
    }

    return result
  }

  override fun markThreadAsRead(threadId: String, updatedAt: Long) {
    readTracker.merge(threadId, updatedAt, ::maxOf)
    readStateUpdates.tryEmit(Unit)
  }
}

private fun ClaudeBackendThread.toAgentSessionThread(readTracker: Map<String, Long>): AgentSessionThread {
  return AgentSessionThread(
    id = id,
    title = title,
    updatedAt = updatedAt,
    archived = archived,
    provider = AgentSessionProvider.CLAUDE,
    originBranch = gitBranch,
    activity = effectiveActivity(readTracker),
  )
}

private fun ClaudeBackendThread.effectiveActivity(readTracker: Map<String, Long>): AgentThreadActivity {
  if (activity == ClaudeSessionActivity.PROCESSING) return AgentThreadActivity.PROCESSING
  val lastSeenAt = readTracker[id] ?: return AgentThreadActivity.READY
  if (updatedAt > lastSeenAt) return AgentThreadActivity.UNREAD
  return AgentThreadActivity.READY
}
