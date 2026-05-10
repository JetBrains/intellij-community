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
import com.intellij.agent.workbench.common.isWorking
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.common.session.AgentSubAgent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceRefreshRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceRefreshResult
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.BaseAgentSessionSource
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

private val LOG = logger<CodexSessionSource>()

internal class CodexSessionSource internal constructor(
  private val backend: CodexSessionBackend,
  private val appServerRefreshHintsProvider: CodexRefreshHintsProvider,
  private val rolloutRefreshHintsProvider: CodexRefreshHintsProvider,
) : BaseAgentSessionSource(provider = AgentSessionProvider.CODEX, canReportExactThreadCount = false) {
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

  override val updateEvents: Flow<AgentSessionSourceUpdateEvent>
    get() = merge(
      backend.updates.map { AgentSessionSourceUpdateEvent(type = AgentSessionSourceUpdate.THREADS_CHANGED) },
      appServerRefreshHintsProvider.updateEvents,
      rolloutRefreshHintsProvider.updateEvents,
      readStateUpdateEvents,
    )

  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    val threads = backend.listThreads(path = path, openProject = openProject)
    trackActiveThreadRead(threads)
    return mapBackendThreadsWithRolloutFallback(mapOf(path to threads))[path].orEmpty()
  }

  override suspend fun prefetchThreads(paths: List<String>): Map<String, List<AgentSessionThread>> {
    val prefetched = backend.prefetchThreads(paths)
    if (prefetched.isEmpty()) return emptyMap()

    prefetched.values.forEach(::trackActiveThreadRead)
    return mapBackendThreadsWithRolloutFallback(prefetched)
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

        trackActiveThreadRead(backendResult.threads)
        val threads = mapBackendThreadsWithRolloutFallback(mapOf(path to backendResult.threads))[path].orEmpty()
        if (backendResult.isComplete) {
          completeThreadsByPath[path] = threads
        }
        else {
          partialThreadsByPath[path] = threads
        }
        if (backendResult.removedThreadIds.isNotEmpty()) {
          removedThreadIdsByPath[path] = backendResult.removedThreadIds
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
    val appServerHints = appServerRefreshHintsProvider.prefetchRefreshHints(
      paths = paths,
      refreshThreadSeedsByPath = refreshThreadSeedsByPath,
    )
    val rolloutHints = rolloutRefreshHintsProvider.prefetchRefreshHints(
      paths = paths,
      refreshThreadSeedsByPath = refreshThreadSeedsByPath,
    )
    val mergedHints = mergeCodexRefreshHints(
      appServerHintsByPath = appServerHints,
      rolloutHintsByPath = rolloutHints,
    )
    absorbActiveThreadReads(mergedHints)
    return filterCodexRefreshHints(mergedHints).mapValues { (_, hints) ->
      hints.toAgentSessionRefreshHints()
    }
  }

  private suspend fun mapBackendThreadsWithRolloutFallback(
    backendThreadsByPath: Map<String, List<CodexBackendThread>>,
  ): Map<String, List<AgentSessionThread>> {
    if (backendThreadsByPath.isEmpty()) {
      return emptyMap()
    }

    val agentThreadsByPath = LinkedHashMap<String, List<AgentSessionThread>>(backendThreadsByPath.size)
    val refreshThreadSeedsByPath = LinkedHashMap<String, Set<AgentSessionRefreshThreadSeed>>()
    for ((path, backendThreads) in backendThreadsByPath) {
      val agentThreads = backendThreads.map(::toAgentSessionThread)
      agentThreadsByPath[path] = agentThreads
      if (agentThreads.isNotEmpty()) {
        refreshThreadSeedsByPath[path] = agentThreads.asSequence()
          .map { thread -> AgentSessionRefreshThreadSeed(threadId = thread.id, updatedAt = thread.updatedAt) }
          .toCollection(LinkedHashSet())
      }
    }
    if (refreshThreadSeedsByPath.isEmpty()) {
      return agentThreadsByPath
    }

    val rolloutHintsByPath = try {
      rolloutRefreshHintsProvider.prefetchRefreshHints(
        paths = refreshThreadSeedsByPath.keys.toList(),
        refreshThreadSeedsByPath = refreshThreadSeedsByPath,
      )
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      LOG.warn("Failed to fetch Codex rollout activity fallback hints", e)
      return agentThreadsByPath
    }
    if (rolloutHintsByPath.isEmpty()) {
      return agentThreadsByPath
    }

    absorbActiveThreadReads(rolloutHintsByPath)

    val result = LinkedHashMap<String, List<AgentSessionThread>>(agentThreadsByPath.size)
    for ((path, agentThreads) in agentThreadsByPath) {
      val rolloutHints = rolloutHintsByPath[path]
      val activityHintsByThreadId = rolloutHints?.activityHintsByThreadId.orEmpty()
      if (activityHintsByThreadId.isEmpty()) {
        result[path] = agentThreads
        continue
      }

      val backendThreadsById = backendThreadsByPath[path]
        .orEmpty()
        .associateBy { backendThread -> backendThread.thread.id }
      var appliedFallbacks = 0
      val threads = agentThreads.map { thread ->
        val rolloutHint = activityHintsByThreadId[thread.id] ?: return@map thread
        if (!shouldKeepRefreshHint(threadId = thread.id, hint = rolloutHint)) {
          return@map thread
        }

        val backendThread = backendThreadsById[thread.id]
        val currentHint = CodexRefreshActivityHint(
          activity = thread.activity,
          updatedAt = backendThread?.thread?.updatedAt ?: thread.updatedAt,
          responseRequired = backendThread?.requiresResponse == true,
        )
        if (!shouldApplyRolloutActivityFallback(currentHint = currentHint, rolloutHint = rolloutHint)) {
          return@map thread
        }

        appliedFallbacks += 1
        LOG.debug {
          "Applied Codex rollout activity fallback " +
          "path=$path threadId=${thread.id} appServerActivity=${thread.activity} rolloutActivity=${rolloutHint.activity} " +
          "appServerUpdatedAt=${currentHint.updatedAt} rolloutUpdatedAt=${rolloutHint.updatedAt} " +
          "appServerResponseRequired=${currentHint.responseRequired} rolloutResponseRequired=${rolloutHint.responseRequired}"
        }
        thread.copy(activity = rolloutHint.activity)
      }
      if (appliedFallbacks > 0) {
        LOG.debug {
          "Applied Codex rollout activity fallbacks path=$path count=$appliedFallbacks hints=${activityHintsByThreadId.size}"
        }
      }
      result[path] = threads
    }
    return result
  }

  private fun trackActiveThreadRead(threads: Iterable<CodexBackendThread>) {
    rememberActiveThreadRead(threads, { it.thread.id }, { it.thread.updatedAt })
  }

  /**
   * Merges the active thread's hint updatedAt into [readTracker] when it is
   * observed without an outstanding response requirement. Must run before
   * [filterCodexRefreshHints] so the filter sees the up-to-date tracker.
   */
  private fun absorbActiveThreadReads(hintsByPath: Map<String, CodexRefreshHints>) {
    val currentActiveId = activeThreadId ?: return
    for ((_, hints) in hintsByPath) {
      val hint = hints.activityHintsByThreadId[currentActiveId] ?: continue
      if (!hint.responseRequired) {
        readTracker.merge(currentActiveId, hint.updatedAt, ::maxOf)
      }
    }
  }

  private fun filterCodexRefreshHints(hintsByPath: Map<String, CodexRefreshHints>): Map<String, CodexRefreshHints> {
    if (hintsByPath.isEmpty()) {
      return emptyMap()
    }

    val filtered = LinkedHashMap<String, CodexRefreshHints>(hintsByPath.size)
    for ((path, hints) in hintsByPath) {
      val filteredActivityHintsByThreadId = LinkedHashMap<String, CodexRefreshActivityHint>(hints.activityHintsByThreadId.size)
      for ((threadId, hint) in hints.activityHintsByThreadId) {
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
        // Rollout can keep TUI-backed working activity ahead of stale app-server state.
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
    rolloutHint.responseRequired -> true
    rolloutHint.activity.isWorking && !currentHint.activity.isWorking -> true
    rolloutHint.activity == AgentThreadActivity.UNREAD -> rolloutHint.updatedAt > currentHint.updatedAt
    !rolloutHint.activity.isWorking -> false
    rolloutHint.updatedAt <= currentHint.updatedAt -> false
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
