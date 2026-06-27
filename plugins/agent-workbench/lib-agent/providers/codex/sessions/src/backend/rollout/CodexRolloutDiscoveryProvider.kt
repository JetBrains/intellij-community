// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.ai.agent.codex.sessions.backend.rollout

import com.intellij.platform.ai.agent.codex.sessions.backend.CodexRefreshHintsProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.nio.file.Path

internal class CodexRolloutDiscoveryProvider(
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
}
