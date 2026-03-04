// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend.rollout

import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.toAgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import kotlinx.coroutines.flow.Flow

internal class CodexRolloutRefreshHintsProvider(
  private val rolloutBackend: CodexRolloutSessionBackend = CodexRolloutSessionBackend(),
) : CodexRefreshHintsProvider {
  override val updates: Flow<Unit>
    get() = rolloutBackend.updates

  override suspend fun prefetchRefreshHints(
    paths: List<String>,
    knownThreadIdsByPath: Map<String, Set<String>>,
  ): Map<String, AgentSessionRefreshHints> {
    if (paths.isEmpty()) return emptyMap()

    val rolloutThreadsByPath = rolloutBackend.prefetchThreads(paths)
    if (rolloutThreadsByPath.isEmpty()) return emptyMap()

    val hintsByPath = LinkedHashMap<String, AgentSessionRefreshHints>(rolloutThreadsByPath.size)
    for ((path, rolloutThreads) in rolloutThreadsByPath) {
      val rebindCandidatesById = LinkedHashMap<String, AgentSessionRebindCandidate>()
      val activityByThreadId = LinkedHashMap<String, AgentThreadActivity>()
      val activityUpdatedAtByThreadId = HashMap<String, Long>()
      val knownThreadIds = knownThreadIdsByPath[path]

      for (rolloutThread in rolloutThreads) {
        val threadId = rolloutThread.thread.id
        if (threadId.isBlank()) continue

        val threadActivity = rolloutThread.activity.toAgentThreadActivity()

        if (knownThreadIds != null && threadId in knownThreadIds) {
          val rolloutUpdatedAt = rolloutThread.thread.updatedAt
          val previousUpdatedAt = activityUpdatedAtByThreadId[threadId]
          if (previousUpdatedAt == null || rolloutUpdatedAt >= previousUpdatedAt) {
            activityUpdatedAtByThreadId[threadId] = rolloutUpdatedAt
            activityByThreadId[threadId] = threadActivity
          }
        }

        if (knownThreadIds == null || threadId in knownThreadIds) {
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

      if (rebindCandidatesById.isEmpty() && activityByThreadId.isEmpty()) {
        continue
      }
      hintsByPath[path] = AgentSessionRefreshHints(
        rebindCandidates = ArrayList(rebindCandidatesById.values),
        activityByThreadId = activityByThreadId,
      )
    }
    return hintsByPath
  }
}
