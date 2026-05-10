// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.common.ClaudeSessionActivity
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceRefreshRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceRefreshResult
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.BaseAgentSessionSource
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

class ClaudeSessionSource(
  private val backend: ClaudeSessionBackend = createDefaultClaudeSessionBackend(),
) : BaseAgentSessionSource(provider = AgentSessionProvider.CLAUDE) {
  override val supportsUpdates: Boolean get() = true

  override val updateEvents: Flow<AgentSessionSourceUpdateEvent>
    get() = merge(
      backend.sessionUpdates.map { update ->
        AgentSessionSourceUpdateEvent(
          type = AgentSessionSourceUpdate.THREADS_CHANGED,
          scopedPaths = update.scopedPaths,
          threadIds = update.threadIds,
        )
      },
      readStateUpdateEvents,
    )

  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    val threads = backend.listThreads(path = path, openProject = openProject)
    val visibleThreads = threads.filterNot(ClaudeBackendThread::archived)
    rememberActiveThreadRead(visibleThreads, ClaudeBackendThread::id, ClaudeBackendThread::updatedAt)
    return visibleThreads.map { it.toAgentSessionThread(readTracker) }
  }

  override suspend fun refreshThreads(request: AgentSessionSourceRefreshRequest): AgentSessionSourceRefreshResult {
    if (!request.isThreadScoped) {
      return super.refreshThreads(request)
    }

    val partialThreadsByPath = LinkedHashMap<String, List<AgentSessionThread>>()
    val completeThreadsByPath = LinkedHashMap<String, List<AgentSessionThread>>()
    val removedThreadIdsByPath = LinkedHashMap<String, Set<String>>()
    val failuresByPath = LinkedHashMap<String, Throwable>()
    for (path in request.paths) {
      try {
        val backendResult = backend.refreshThreads(path = path, threadIds = request.threadIds, openProject = null)
        if (backendResult == null) {
          completeThreadsByPath[path] = listThreads(path = path, openProject = null)
          continue
        }

        val visibleThreads = backendResult.threads.filterNot(ClaudeBackendThread::archived)
        rememberActiveThreadRead(visibleThreads, ClaudeBackendThread::id, ClaudeBackendThread::updatedAt)
        val archivedThreadIds = backendResult.threads
          .asSequence()
          .filter(ClaudeBackendThread::archived)
          .map(ClaudeBackendThread::id)
          .toCollection(LinkedHashSet())
        val removedThreadIds = LinkedHashSet<String>().apply {
          addAll(backendResult.removedThreadIds)
          addAll(archivedThreadIds)
        }
        val threads = visibleThreads.map { it.toAgentSessionThread(readTracker) }
        if (backendResult.isComplete) {
          completeThreadsByPath[path] = threads
        }
        else {
          partialThreadsByPath[path] = threads
        }
        if (removedThreadIds.isNotEmpty()) {
          removedThreadIdsByPath[path] = removedThreadIds
        }
      }
      catch (e: Throwable) {
        if (e is CancellationException) throw e
        failuresByPath[path] = e
      }
    }

    return AgentSessionSourceRefreshResult(
      completeThreadsByPath = completeThreadsByPath,
      partialThreadsByPath = partialThreadsByPath,
      removedThreadIdsByPath = removedThreadIdsByPath,
      failuresByPath = failuresByPath,
    )
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
      val activityHintsByThreadId = LinkedHashMap<String, AgentThreadActivity>()
      for (thread in visibleThreads) {
        if (thread.id in knownIds) {
          activityHintsByThreadId[thread.id] = thread.effectiveActivity(readTracker)
        }
      }
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
      if (rebindCandidates.isNotEmpty() || activityHintsByThreadId.isNotEmpty()) {
        result[path] = AgentSessionRefreshHints(
          rebindCandidates = rebindCandidates,
          activityByThreadId = activityHintsByThreadId,
        )
      }
    }

    return result
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
  return when (activity) {
    ClaudeSessionActivity.PROCESSING -> AgentThreadActivity.PROCESSING
    ClaudeSessionActivity.NEEDS_INPUT -> AgentThreadActivity.NEEDS_INPUT
    ClaudeSessionActivity.READY -> {
      val lastSeenAt = readTracker[id] ?: return AgentThreadActivity.READY
      if (updatedAt > lastSeenAt) AgentThreadActivity.UNREAD else AgentThreadActivity.READY
    }
  }
}
