// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import kotlinx.coroutines.flow.Flow

internal data class CodexRefreshActivityHint(
  @JvmField val activity: AgentThreadActivity,
  @JvmField val updatedAt: Long,
  @JvmField val responseRequired: Boolean = false,
)

internal data class CodexRefreshHints(
  @JvmField val rebindCandidates: List<AgentSessionRebindCandidate> = emptyList(),
  @JvmField val activityHintsByThreadId: Map<String, CodexRefreshActivityHint> = emptyMap(),
)

internal fun CodexRefreshHints.toAgentSessionRefreshHints(): AgentSessionRefreshHints {
  return AgentSessionRefreshHints(
    rebindCandidates = rebindCandidates,
    activityByThreadId = activityHintsByThreadId.mapValues { (_, hint) -> hint.activity },
  )
}

internal interface CodexRefreshHintsProvider {
  val updateEvents: Flow<AgentSessionSourceUpdateEvent>

  suspend fun prefetchRefreshHints(
    paths: List<String>,
    knownThreadIdsByPath: Map<String, Set<String>>,
  ): Map<String, CodexRefreshHints>
}
