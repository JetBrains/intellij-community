// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.ai.agent.codex.sessions.backend.rollout

import com.intellij.platform.ai.agent.codex.common.CodexThreadSourceKind
import com.intellij.platform.ai.agent.codex.sessions.backend.CodexRefreshHints
import com.intellij.platform.ai.agent.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.platform.ai.agent.codex.sessions.backend.toAgentThreadActivity
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRefreshThreadSeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.nio.file.Path

internal class CodexRolloutRefreshHintsProvider(
  private val rolloutBackend: CodexRolloutSessionBackend = CodexRolloutSessionBackend(),
  private val activeFileChangeFlow: (Collection<Path>) -> Flow<Path> = { emptyFlow() },
) : CodexRefreshHintsProvider {
  override val updateEvents: Flow<AgentSessionSourceUpdateEvent>
    get() = rolloutBackend.sessionUpdates

  override fun activeThreadUpdateEvents(path: String, threadId: String): Flow<AgentSessionSourceUpdateEvent> {
    return rolloutBackend.activeThreadUpdateEvents(
      path = path,
      threadId = threadId,
      fileChangeFlow = activeFileChangeFlow,
    )
  }

  override suspend fun prefetchRefreshHints(
    paths: List<String>,
    refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
  ): Map<String, CodexRefreshHints> {
    if (paths.isEmpty()) return emptyMap()

    val hintsByPath = LinkedHashMap<String, CodexRefreshHints>(paths.size)
    for (path in paths) {
      val knownThreadIds = refreshThreadSeedsByPath[path].orEmpty().asSequence().map { it.threadId }.toCollection(LinkedHashSet())
      val rolloutThreads = rolloutBackend.prefetchThreads(listOf(path))[path].orEmpty()
      if (rolloutThreads.isEmpty()) {
        continue
      }

      val rebindCandidatesById = LinkedHashMap<String, AgentSessionRebindCandidate>()

      for ((thread, activity) in rolloutThreads) {
        val threadId = thread.id
        if (threadId.isBlank()) continue
        if (
          threadId in knownThreadIds ||
          thread.sourceKind != CodexThreadSourceKind.CLI
        ) {
          continue
        }

        val candidate = AgentSessionRebindCandidate(
          threadId = threadId,
          title = thread.title,
          updatedAt = thread.updatedAt,
          activity = activity.toAgentThreadActivity(),
        )
        val previousCandidate = rebindCandidatesById[threadId]
        if (previousCandidate == null || candidate.updatedAt >= previousCandidate.updatedAt) {
          rebindCandidatesById[threadId] = candidate
        }
      }

      if (rebindCandidatesById.isEmpty()) {
        continue
      }
      hintsByPath[path] = CodexRefreshHints(
        rebindCandidates = ArrayList(rebindCandidatesById.values),
      )
    }
    return hintsByPath
  }
}
