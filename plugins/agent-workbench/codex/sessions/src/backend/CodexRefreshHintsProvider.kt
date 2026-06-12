// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadActivityUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadPresentationUpdate
import com.intellij.agent.workbench.sessions.core.providers.mergeAgentSessionThreadPresentationUpdates
import com.intellij.agent.workbench.sessions.core.providers.toPresentationUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal data class CodexRefreshActivityHint(
  @JvmField val activity: AgentThreadActivity,
  @JvmField val updatedAt: Long,
  @JvmField val responseRequired: Boolean = false,
  @JvmField val verifiedFresh: Boolean = false,
  @JvmField val summaryActivity: AgentThreadActivity? = activity,
  @JvmField val hasSummaryActivityHint: Boolean = true,
)

internal data class CodexRefreshHints(
  @JvmField val rebindCandidates: List<AgentSessionRebindCandidate> = emptyList(),
  @JvmField val activityHintsByThreadId: Map<String, CodexRefreshActivityHint> = emptyMap(),
  @JvmField val presentationUpdatesByThreadId: Map<String, AgentSessionThreadPresentationUpdate> = emptyMap(),
)

internal fun CodexRefreshHints.toAgentSessionRefreshHints(): AgentSessionRefreshHints {
  val activityUpdatesByThreadId = activityHintsByThreadId.mapValues { (_, hint) ->
    AgentSessionThreadActivityUpdate(
      activityReport = AgentThreadActivityReport(
        rowActivity = hint.activity,
        chromeActivity = if (hint.hasSummaryActivityHint) hint.summaryActivity else null,
      ),
      updatesChromeActivity = hint.hasSummaryActivityHint,
      updatedAt = hint.updatedAt,
    )
  }
  return AgentSessionRefreshHints(
    rebindCandidates = rebindCandidates,
    activityUpdatesByThreadId = activityUpdatesByThreadId,
    presentationUpdatesByThreadId = mergeCodexPresentationUpdates(
      activityUpdatesByThreadId.mapValues { (_, update) -> update.toPresentationUpdate() },
      presentationUpdatesByThreadId,
    ),
  )
}

private fun mergeCodexPresentationUpdates(
  existing: Map<String, AgentSessionThreadPresentationUpdate>,
  incoming: Map<String, AgentSessionThreadPresentationUpdate>,
): Map<String, AgentSessionThreadPresentationUpdate> {
  if (existing.isEmpty()) return incoming
  if (incoming.isEmpty()) return existing
  val merged = LinkedHashMap<String, AgentSessionThreadPresentationUpdate>(existing.size + incoming.size)
  merged.putAll(existing)
  for ((threadId, incomingUpdate) in incoming) {
    val existingUpdate = merged[threadId]
    merged[threadId] = if (existingUpdate == null) incomingUpdate else mergeAgentSessionThreadPresentationUpdates(existingUpdate, incomingUpdate)
  }
  return merged
}

internal interface CodexRefreshHintsProvider {
  val updateEvents: Flow<AgentSessionSourceUpdateEvent>

  fun activeThreadUpdateEvents(path: String, threadId: String): Flow<AgentSessionSourceUpdateEvent> = emptyFlow()

  suspend fun prefetchRefreshHints(
    paths: List<String>,
    refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
  ): Map<String, CodexRefreshHints>
}
