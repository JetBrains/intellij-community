// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CodexSessionSourceRefreshHintsTest {
  @Test
  fun mergePrefersAppServerRebindCandidatesButAllowsRolloutUnreadOverride() {
    val appServerHintsByPath = mapOf(
      "/work/project" to AgentSessionRefreshHints(
        rebindCandidates = listOf(
          rebindCandidate(
            threadId = "shared",
            title = "shared-from-app",
            updatedAt = 200L,
            activity = AgentThreadActivity.PROCESSING,
          ),
          rebindCandidate(
            threadId = "app-only",
            title = "app-only",
            updatedAt = 150L,
            activity = AgentThreadActivity.REVIEWING,
          ),
        ),
        activityByThreadId = mapOf(
          "thread-shared" to AgentThreadActivity.REVIEWING,
          "thread-unread" to AgentThreadActivity.UNREAD,
        ),
      )
    )
    val rolloutHintsByPath = mapOf(
      "/work/project" to AgentSessionRefreshHints(
        rebindCandidates = listOf(
          rebindCandidate(
            threadId = "shared",
            title = "shared-from-rollout",
            updatedAt = 220L,
            activity = AgentThreadActivity.UNREAD,
          ),
          rebindCandidate(
            threadId = "rollout-only",
            title = "rollout-only",
            updatedAt = 120L,
            activity = AgentThreadActivity.READY,
          ),
        ),
        activityByThreadId = mapOf(
          "thread-shared" to AgentThreadActivity.UNREAD,
          "thread-unread" to AgentThreadActivity.READY,
          "thread-rollout-only" to AgentThreadActivity.PROCESSING,
        ),
      )
    )

    val merged = mergeCodexRefreshHints(
      appServerHintsByPath = appServerHintsByPath,
      rolloutHintsByPath = rolloutHintsByPath,
    )

    val hints = merged.getValue("/work/project")
    assertThat(hints.activityByThreadId).containsExactlyInAnyOrderEntriesOf(
      mapOf(
        "thread-shared" to AgentThreadActivity.UNREAD,
        "thread-unread" to AgentThreadActivity.UNREAD,
        "thread-rollout-only" to AgentThreadActivity.PROCESSING,
      )
    )

    val rebindById = hints.rebindCandidates.associateBy { it.threadId }
    assertThat(rebindById.keys).containsExactlyInAnyOrder("shared", "app-only", "rollout-only")
    assertThat(rebindById.getValue("shared").title).isEqualTo("shared-from-app")
    assertThat(rebindById.getValue("shared").updatedAt).isEqualTo(200L)
    assertThat(rebindById.getValue("app-only").title).isEqualTo("app-only")
    assertThat(rebindById.getValue("rollout-only").title).isEqualTo("rollout-only")
  }

  @Test
  fun mergeReturnsNonEmptySourceWhenOtherIsEmpty() {
    val hints = mapOf(
      "/work/project" to AgentSessionRefreshHints(
        rebindCandidates = listOf(
          rebindCandidate(
            threadId = "thread-1",
            title = "Thread 1",
            updatedAt = 100L,
            activity = AgentThreadActivity.READY,
          )
        )
      )
    )

    assertThat(mergeCodexRefreshHints(appServerHintsByPath = hints, rolloutHintsByPath = emptyMap())).isEqualTo(hints)
    assertThat(mergeCodexRefreshHints(appServerHintsByPath = emptyMap(), rolloutHintsByPath = hints)).isEqualTo(hints)
  }
}

private fun rebindCandidate(
  threadId: String,
  title: String,
  updatedAt: Long,
  activity: AgentThreadActivity,
): AgentSessionRebindCandidate {
  return AgentSessionRebindCandidate(
    threadId = threadId,
    title = title,
    updatedAt = updatedAt,
    activity = activity,
  )
}
