// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

// @spec community/plugins/agent-workbench/spec/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-sessions-codex-rollout-source.spec.md

import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivity
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.appserver.SharedCodexAppServerService
import com.intellij.agent.workbench.codex.sessions.backend.createDefaultCodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexRolloutRefreshHintsProvider
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

internal class CodexSessionSource private constructor(
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

  override val updates: Flow<Unit>
    get() = merge(
      backend.updates,
      appServerRefreshHintsProvider.updates,
      rolloutRefreshHintsProvider.updates,
    )

  override val updateEvents: Flow<AgentSessionSourceUpdate>
    get() = merge(
      backend.updates.map { AgentSessionSourceUpdate.THREADS_CHANGED },
      appServerRefreshHintsProvider.updates.map { AgentSessionSourceUpdate.HINTS_CHANGED },
      rolloutRefreshHintsProvider.updates.map { AgentSessionSourceUpdate.HINTS_CHANGED },
    )

  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    return backend.listThreads(path = path, openProject = openProject).map { toAgentSessionThread(it) }
  }

  override suspend fun prefetchThreads(paths: List<String>): Map<String, List<AgentSessionThread>> {
    val prefetched = backend.prefetchThreads(paths)
    if (prefetched.isEmpty()) return emptyMap()
    return prefetched.mapValues { (_, threads) ->
      threads.map { toAgentSessionThread(it) }
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
    return mergeCodexRefreshHints(
      appServerHintsByPath = appServerHints,
      rolloutHintsByPath = rolloutHints,
    )
  }
}

internal fun mergeCodexRefreshHints(
  appServerHintsByPath: Map<String, AgentSessionRefreshHints>,
  rolloutHintsByPath: Map<String, AgentSessionRefreshHints>,
): Map<String, AgentSessionRefreshHints> {
  if (appServerHintsByPath.isEmpty()) {
    return rolloutHintsByPath
  }
  if (rolloutHintsByPath.isEmpty()) {
    return appServerHintsByPath
  }

  val merged = LinkedHashMap<String, AgentSessionRefreshHints>()
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
    val mergedActivityByThreadId = LinkedHashMap<String, AgentThreadActivity>()
    appHints?.activityByThreadId?.forEach { (threadId, activity) ->
      mergedActivityByThreadId[threadId] = activity
    }
    rolloutHints?.activityByThreadId?.forEach { (threadId, activity) ->
      if (shouldApplyRolloutActivityFallback(
          currentActivity = mergedActivityByThreadId[threadId],
          rolloutActivity = activity,
        )) {
        // Rollout fallback is intentionally narrow: it can raise stale API activity to unread.
        mergedActivityByThreadId[threadId] = activity
      }
    }

    if (mergedRebindCandidates.isEmpty() && mergedActivityByThreadId.isEmpty()) {
      continue
    }
    merged[path] = AgentSessionRefreshHints(
      rebindCandidates = mergedRebindCandidates,
      activityByThreadId = mergedActivityByThreadId,
    )
  }
  return merged
}

private fun shouldApplyRolloutActivityFallback(
  currentActivity: AgentThreadActivity?,
  rolloutActivity: AgentThreadActivity,
): Boolean {
  return currentActivity == null || rolloutActivity == AgentThreadActivity.UNREAD
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
    val existing = mergedByThreadId[candidate.threadId]
    if (existing == null || candidate.updatedAt >= existing.updatedAt) {
      mergedByThreadId[candidate.threadId] = candidate
    }
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
