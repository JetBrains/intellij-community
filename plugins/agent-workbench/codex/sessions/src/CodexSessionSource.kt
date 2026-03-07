// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

// @spec community/plugins/agent-workbench/spec/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-sessions-codex-rollout-source.spec.md

import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshActivityHint
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHints
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivity
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.appserver.SharedCodexAppServerService
import com.intellij.agent.workbench.codex.sessions.backend.createDefaultCodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexRolloutRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.toAgentSessionRefreshHints
import com.intellij.agent.workbench.codex.sessions.backend.toAgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.BaseAgentSessionSource
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.util.concurrent.ConcurrentHashMap

internal class CodexSessionSource internal constructor(
  private val backend: CodexSessionBackend,
  private val appServerRefreshHintsProvider: CodexRefreshHintsProvider,
  private val rolloutRefreshHintsProvider: CodexRefreshHintsProvider,
) : BaseAgentSessionSource(provider = AgentSessionProvider.CODEX, canReportExactThreadCount = false) {
  private val readTracker = ConcurrentHashMap<String, Long>()
  private val readStateUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  @Volatile private var activeThreadId: String? = null

  constructor(
    backend: CodexSessionBackend = createDefaultCodexSessionBackend(),
    sharedAppServerService: SharedCodexAppServerService = service(),
  ) : this(
    backend = backend,
    appServerRefreshHintsProvider = CodexAppServerRefreshHintsProvider(
      readThreadActivitySnapshot = sharedAppServerService::readThreadActivitySnapshot,
      notifications = sharedAppServerService.notifications,
    ),
    rolloutRefreshHintsProvider = CodexRolloutRefreshHintsProvider(),
  )

  override val supportsUpdates: Boolean
    get() = true

  override val updates: Flow<Unit>
    get() = merge(
      backend.updates,
      appServerRefreshHintsProvider.updates,
      rolloutRefreshHintsProvider.updates,
      readStateUpdates,
    )

  override val updateEvents: Flow<AgentSessionSourceUpdate>
    get() = merge(
      backend.updates.map { AgentSessionSourceUpdate.THREADS_CHANGED },
      appServerRefreshHintsProvider.updates.map { AgentSessionSourceUpdate.HINTS_CHANGED },
      rolloutRefreshHintsProvider.updates.map { AgentSessionSourceUpdate.HINTS_CHANGED },
      readStateUpdates.map { AgentSessionSourceUpdate.HINTS_CHANGED },
    )

  override fun setActiveThreadId(threadId: String?) {
    activeThreadId = threadId
  }

  override fun markThreadAsRead(threadId: String, updatedAt: Long) {
    readTracker.merge(threadId, updatedAt, ::maxOf)
    readStateUpdates.tryEmit(Unit)
  }

  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    val threads = backend.listThreads(path = path, openProject = openProject)
    trackActiveThreadRead(threads)
    return threads.map(::toAgentSessionThread)
  }

  override suspend fun prefetchThreads(paths: List<String>): Map<String, List<AgentSessionThread>> {
    val prefetched = backend.prefetchThreads(paths)
    if (prefetched.isEmpty()) return emptyMap()

    prefetched.values.forEach(::trackActiveThreadRead)
    return prefetched.mapValues { (_, threads) ->
      threads.map(::toAgentSessionThread)
    }
  }

  override suspend fun prefetchRefreshHints(
    paths: List<String>,
    knownThreadIdsByPath: Map<String, Set<String>>,
  ): Map<String, AgentSessionRefreshHints> {
    val appServerHints = appServerRefreshHintsProvider.prefetchRefreshHints(
      paths = paths,
      knownThreadIdsByPath = knownThreadIdsByPath,
    )
    val rolloutHints = rolloutRefreshHintsProvider.prefetchRefreshHints(
      paths = paths,
      knownThreadIdsByPath = knownThreadIdsByPath,
    )
    return filterCodexRefreshHints(
      mergeCodexRefreshHints(
        appServerHintsByPath = appServerHints,
        rolloutHintsByPath = rolloutHints,
      )
    ).mapValues { (_, hints) ->
      hints.toAgentSessionRefreshHints()
    }
  }

  private fun trackActiveThreadRead(threads: Iterable<CodexBackendThread>) {
    val currentActiveId = activeThreadId ?: return
    for (thread in threads) {
      if (thread.thread.id != currentActiveId) {
        continue
      }
      readTracker.merge(thread.thread.id, thread.thread.updatedAt, ::maxOf)
      return
    }
  }

  private fun filterCodexRefreshHints(hintsByPath: Map<String, CodexRefreshHints>): Map<String, CodexRefreshHints> {
    if (hintsByPath.isEmpty()) {
      return emptyMap()
    }

    val currentActiveId = activeThreadId
    val filtered = LinkedHashMap<String, CodexRefreshHints>(hintsByPath.size)
    for ((path, hints) in hintsByPath) {
      val filteredActivityHintsByThreadId = LinkedHashMap<String, CodexRefreshActivityHint>(hints.activityHintsByThreadId.size)
      for ((threadId, hint) in hints.activityHintsByThreadId) {
        if (currentActiveId == threadId && !hint.responseRequired) {
          readTracker.merge(threadId, hint.updatedAt, ::maxOf)
        }

        if (shouldKeepRefreshHint(threadId = threadId, hint = hint)) {
          filteredActivityHintsByThreadId[threadId] = hint
        }
      }

      if (hints.rebindCandidates.isEmpty() && filteredActivityHintsByThreadId.isEmpty()) {
        continue
      }
      filtered[path] = CodexRefreshHints(
        rebindCandidates = hints.rebindCandidates,
        activityHintsByThreadId = filteredActivityHintsByThreadId,
      )
    }
    return filtered
  }

  private fun shouldKeepRefreshHint(threadId: String, hint: CodexRefreshActivityHint): Boolean {
    if (hint.activity != AgentThreadActivity.UNREAD || hint.responseRequired) {
      return true
    }

    val lastReadAt = readTracker[threadId] ?: return true
    return hint.updatedAt > lastReadAt
  }
}

internal fun mergeCodexRefreshHints(
  appServerHintsByPath: Map<String, CodexRefreshHints>,
  rolloutHintsByPath: Map<String, CodexRefreshHints>,
): Map<String, CodexRefreshHints> {
  if (appServerHintsByPath.isEmpty()) {
    return rolloutHintsByPath
  }
  if (rolloutHintsByPath.isEmpty()) {
    return appServerHintsByPath
  }

  val merged = LinkedHashMap<String, CodexRefreshHints>()
  val allPaths = LinkedHashSet<String>(appServerHintsByPath.keys.size + rolloutHintsByPath.keys.size)
  allPaths.addAll(appServerHintsByPath.keys)
  allPaths.addAll(rolloutHintsByPath.keys)

  for (path in allPaths) {
    val appHints = appServerHintsByPath[path]
    val rolloutHints = rolloutHintsByPath[path]
    val mergedRebindCandidates = mergeRebindCandidates(
      primary = appHints?.rebindCandidates.orEmpty(),
      fallback = rolloutHints?.rebindCandidates.orEmpty(),
    )
    val mergedActivityHintsByThreadId = LinkedHashMap<String, CodexRefreshActivityHint>()
    appHints?.activityHintsByThreadId?.forEach { (threadId, hint) ->
      mergedActivityHintsByThreadId[threadId] = hint
    }
    rolloutHints?.activityHintsByThreadId?.forEach { (threadId, hint) ->
      if (shouldApplyRolloutActivityFallback(
          currentHint = mergedActivityHintsByThreadId[threadId],
          rolloutHint = hint,
        )) {
        // Rollout fallback is intentionally narrow: it can raise stale API activity to unread.
        mergedActivityHintsByThreadId[threadId] = hint
      }
    }

    if (mergedRebindCandidates.isEmpty() && mergedActivityHintsByThreadId.isEmpty()) {
      continue
    }
    merged[path] = CodexRefreshHints(
      rebindCandidates = mergedRebindCandidates,
      activityHintsByThreadId = mergedActivityHintsByThreadId,
    )
  }
  return merged
}

private fun shouldApplyRolloutActivityFallback(
  currentHint: CodexRefreshActivityHint?,
  rolloutHint: CodexRefreshActivityHint,
): Boolean {
  return when {
    currentHint == null -> true
    currentHint.responseRequired -> false
    rolloutHint.activity != AgentThreadActivity.UNREAD -> false
    else -> true
  }
}

private fun mergeRebindCandidates(
  primary: List<AgentSessionRebindCandidate>,
  fallback: List<AgentSessionRebindCandidate>,
): List<AgentSessionRebindCandidate> {
  if (primary.isEmpty()) return fallback
  if (fallback.isEmpty()) return primary

  val mergedByThreadId = LinkedHashMap<String, AgentSessionRebindCandidate>(primary.size + fallback.size)
  primary.forEach { candidate ->
    mergedByThreadId[candidate.threadId] = candidate
  }
  fallback.forEach { candidate ->
    mergedByThreadId.putIfAbsent(candidate.threadId, candidate)
  }
  return mergedByThreadId.values.toList()
}

private fun toAgentSessionThread(thread: CodexBackendThread): AgentSessionThread {
  return toAgentSessionThread(thread = thread.thread, activity = thread.activity)
}

private fun toAgentSessionThread(thread: CodexThread, activity: CodexSessionActivity): AgentSessionThread {
  return AgentSessionThread(
    id = thread.id,
    title = thread.title,
    updatedAt = thread.updatedAt,
    archived = thread.archived,
    provider = AgentSessionProvider.CODEX,
    subAgents = thread.subAgents.map { AgentSubAgent(it.id, it.name) },
    originBranch = thread.gitBranch,
    activity = activity.toAgentThreadActivity(),
  )
}
