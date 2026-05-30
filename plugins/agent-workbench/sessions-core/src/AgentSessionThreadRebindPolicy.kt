// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object AgentSessionThreadRebindPolicy {
  const val PENDING_THREAD_MATCH_PRE_WINDOW_MS: Long = 20_000L
  const val PENDING_THREAD_MATCH_POST_WINDOW_MS: Long = 120_000L
  const val PENDING_THREAD_NO_BASELINE_AUTO_BIND_MAX_AGE_MS: Long = PENDING_THREAD_MATCH_POST_WINDOW_MS
  const val PENDING_THREAD_REFRESH_RETRY_INTERVAL_MS: Long = 2_000L
  const val CONCRETE_CODEX_NEW_THREAD_MATCH_PRE_WINDOW_MS: Long = 5_000L
  const val CONCRETE_CODEX_NEW_THREAD_MATCH_POST_WINDOW_MS: Long = 30_000L
  const val CONCRETE_CODEX_NEW_THREAD_REBIND_MAX_AGE_MS: Long = CONCRETE_CODEX_NEW_THREAD_MATCH_POST_WINDOW_MS
  const val CONCRETE_CODEX_NEW_THREAD_REFRESH_RETRY_INTERVAL_MS: Long = 2_000L

  fun isConcreteCodexNewThreadRebindAnchorActive(rebindRequestedAtMs: Long, currentTimeMs: Long): Boolean {
    return currentTimeMs >= rebindRequestedAtMs &&
           currentTimeMs - rebindRequestedAtMs < CONCRETE_CODEX_NEW_THREAD_REBIND_MAX_AGE_MS
  }

  fun concreteCodexNewThreadRefreshRetryDelayMs(
    rebindRequestedAtMs: Long,
    currentTimeMs: Long,
    retryIntervalMs: Long = CONCRETE_CODEX_NEW_THREAD_REFRESH_RETRY_INTERVAL_MS,
  ): Long? {
    if (currentTimeMs < rebindRequestedAtMs) {
      return null
    }
    val retryDeadlineMs = rebindRequestedAtMs + CONCRETE_CODEX_NEW_THREAD_REBIND_MAX_AGE_MS
    val remainingMs = retryDeadlineMs - currentTimeMs
    if (remainingMs <= 0L) {
      return null
    }
    return minOf(retryIntervalMs, remainingMs)
  }
}
