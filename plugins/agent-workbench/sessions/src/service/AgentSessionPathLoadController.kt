// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal const val DEFAULT_AGENT_SESSION_LOADING_DELAY_MS: Long = 300L

internal class AgentSessionPathLoadController(
  private val loadingDelayMs: Long = DEFAULT_AGENT_SESSION_LOADING_DELAY_MS,
) {
  suspend fun <T> runWithDelayedLoading(
    providerLoadStates: () -> Map<AgentSessionProvider, AgentSessionProviderLoadState>,
    publishLoading: (Map<AgentSessionProvider, AgentSessionProviderLoadState>) -> Unit,
    block: suspend () -> T,
  ): T {
    if (loadingDelayMs <= 0L) {
      providerLoadStates().takeIf { it.isNotEmpty() }?.let(publishLoading)
      return block()
    }

    return coroutineScope {
      val loadingJob = launch {
        delay(loadingDelayMs.milliseconds)
        providerLoadStates().takeIf { it.isNotEmpty() }?.let(publishLoading)
      }
      try {
        block()
      }
      finally {
        loadingJob.cancelAndJoin()
      }
    }
  }
}
