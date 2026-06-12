// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend.rollout

import com.intellij.agent.workbench.codex.common.CodexThreadSourceKind
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshActivityHint
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHints
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.agent.workbench.codex.sessions.backend.toAgentThreadActivity
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadPresentationUpdate
import kotlinx.coroutines.flow.Flow

internal class CodexRolloutRefreshHintsProvider(
  private val rolloutBackend: CodexRolloutSessionBackend = CodexRolloutSessionBackend(),
) : CodexRefreshHintsProvider {
  override val updateEvents: Flow<AgentSessionSourceUpdateEvent>
    get() = rolloutBackend.sessionUpdates

  override fun activeThreadUpdateEvents(path: String, threadId: String): Flow<AgentSessionSourceUpdateEvent> {
    return rolloutBackend.activeThreadUpdateEvents(path = path, threadId = threadId)
  }

  override suspend fun prefetchRefreshHints(
    paths: List<String>,
    refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
  ): Map<String, CodexRefreshHints> {
    if (paths.isEmpty()) return emptyMap()

    val hintsByPath = LinkedHashMap<String, CodexRefreshHints>(paths.size)
    for (path in paths) {
      val knownThreadIds = refreshThreadSeedsByPath[path].orEmpty().asSequence().map { it.threadId }.toCollection(LinkedHashSet())
      val rolloutThreads = if (knownThreadIds.isEmpty()) {
        rolloutBackend.prefetchThreads(listOf(path))[path].orEmpty()
      }
      else {
        rolloutBackend.prefetchThreads(listOf(path))[path].orEmpty()
      }
      if (rolloutThreads.isEmpty()) {
        continue
      }

      val activityThreads = if (knownThreadIds.isEmpty()) {
        rolloutThreads
      }
      else {
        rolloutBackend.refreshThreads(path = path, threadIds = knownThreadIds, openProject = null)?.threads.orEmpty()
      }

      val rebindCandidatesById = LinkedHashMap<String, AgentSessionRebindCandidate>()
      val activityHintsByThreadId = LinkedHashMap<String, CodexRefreshActivityHint>()
      val presentationUpdatesByThreadId = LinkedHashMap<String, AgentSessionThreadPresentationUpdate>()
      val activityUpdatedAtByThreadId = HashMap<String, Long>()

      for (rolloutThread in activityThreads) {
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
              summaryActivity = rolloutThread.summaryActivity?.toAgentThreadActivity(),
            )
            if (rolloutThread.hasExplicitTitle) {
              presentationUpdatesByThreadId[threadId] = AgentSessionThreadPresentationUpdate(
                title = rolloutThread.thread.title,
                updatedAt = rolloutUpdatedAt,
              )
            }
          }
        }
      }

      for (rolloutThread in rolloutThreads) {
        val threadId = rolloutThread.thread.id
        if (threadId.isBlank()) continue
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
          activity = rolloutThread.activity.toAgentThreadActivity(),
        )
        val previousCandidate = rebindCandidatesById[threadId]
        if (previousCandidate == null || candidate.updatedAt >= previousCandidate.updatedAt) {
          rebindCandidatesById[threadId] = candidate
        }
      }

      if (rebindCandidatesById.isEmpty() && activityHintsByThreadId.isEmpty() && presentationUpdatesByThreadId.isEmpty()) {
        continue
      }
      hintsByPath[path] = CodexRefreshHints(
        rebindCandidates = ArrayList(rebindCandidatesById.values),
        activityHintsByThreadId = activityHintsByThreadId,
        presentationUpdatesByThreadId = presentationUpdatesByThreadId,
      )
    }
    return hintsByPath
  }
}
