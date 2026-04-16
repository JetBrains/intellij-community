// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend.rollout

import com.intellij.agent.workbench.codex.common.CodexThreadSourceKind
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshActivityHint
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHints
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.toAgentThreadActivity
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class CodexRolloutRefreshHintsProvider(
  private val rolloutBackend: CodexRolloutSessionBackend = CodexRolloutSessionBackend(),
) : CodexRefreshHintsProvider {
  override val updateEvents: Flow<AgentSessionSourceUpdateEvent>
    get() = rolloutBackend.updates.map { AgentSessionSourceUpdateEvent(type = AgentSessionSourceUpdate.HINTS_CHANGED) }

  override suspend fun prefetchRefreshHints(
    paths: List<String>,
    refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
  ): Map<String, CodexRefreshHints> {
    if (paths.isEmpty()) return emptyMap()

    val rolloutThreadsByPath = rolloutBackend.prefetchThreads(paths)
    if (rolloutThreadsByPath.isEmpty()) return emptyMap()

    val hintsByPath = LinkedHashMap<String, CodexRefreshHints>(rolloutThreadsByPath.size)
    for ((path, rolloutThreads) in rolloutThreadsByPath) {
      val rebindCandidatesById = LinkedHashMap<String, AgentSessionRebindCandidate>()
      val activityHintsByThreadId = LinkedHashMap<String, CodexRefreshActivityHint>()
      val activityUpdatedAtByThreadId = HashMap<String, Long>()
      val knownThreadIds = refreshThreadSeedsByPath[path].orEmpty().asSequence().map { it.threadId }.toCollection(LinkedHashSet())

      for (rolloutThread in rolloutThreads) {
        val threadId = rolloutThread.thread.id
        if (threadId.isBlank()) continue

        val threadActivity = rolloutThread.activity.toAgentThreadActivity()

        if (threadId in knownThreadIds) {
          val rolloutUpdatedAt = rolloutThread.thread.updatedAt
          val previousUpdatedAt = activityUpdatedAtByThreadId[threadId]
          if (previousUpdatedAt == null || rolloutUpdatedAt >= previousUpdatedAt) {
            activityUpdatedAtByThreadId[threadId] = rolloutUpdatedAt
            activityHintsByThreadId[threadId] = CodexRefreshActivityHint(
              activity = threadActivity,
              updatedAt = rolloutUpdatedAt,
              responseRequired = rolloutThread.requiresResponse,
            )
          }
        }

        if (
          threadId in knownThreadIds ||
          rolloutThread.thread.sourceKind != CodexThreadSourceKind.CLI
        ) {
          continue
        }

        val candidate = AgentSessionRebindCandidate(
          threadId = threadId,
          title = rolloutThread.thread.title,
          updatedAt = rolloutThread.thread.updatedAt,
          activity = threadActivity,
        )
        val previousCandidate = rebindCandidatesById[threadId]
        if (previousCandidate == null || candidate.updatedAt >= previousCandidate.updatedAt) {
          rebindCandidatesById[threadId] = candidate
        }
      }

      if (rebindCandidatesById.isEmpty() && activityHintsByThreadId.isEmpty()) {
        continue
      }
      hintsByPath[path] = CodexRefreshHints(
        rebindCandidates = ArrayList(rebindCandidatesById.values),
        activityHintsByThreadId = activityHintsByThreadId,
      )
    }
    return hintsByPath
  }
}
