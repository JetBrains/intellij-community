// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshActivityHint
import com.intellij.agent.workbench.codex.sessions.backend.CodexRefreshHints
import com.intellij.agent.workbench.codex.sessions.backend.toAgentSessionRefreshHints
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadPresentationUpdate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexSessionSourceRefreshHintsTest {
  @Test
  fun conversionCombinesExplicitPresentationTitleWithActivityHint() {
    val hints = CodexRefreshHints(
      activityHintsByThreadId = mapOf(
        "thread-a" to refreshHint(activity = AgentThreadActivity.PROCESSING, updatedAt = 200L)
      ),
      presentationUpdatesByThreadId = mapOf(
        "thread-a" to AgentSessionThreadPresentationUpdate(title = "JSONL title", updatedAt = 200L)
      ),
    ).toAgentSessionRefreshHints()

    val update = hints.presentationUpdatesByThreadId.getValue("thread-a")
    assertThat(update.title).isEqualTo("JSONL title")
    assertThat(update.activityReport).isEqualTo(AgentThreadActivityReport(AgentThreadActivity.PROCESSING))
    assertThat(update.updatedAt).isEqualTo(200L)
  }

  @Test
  fun mergePrefersAppServerRebindCandidatesButAllowsRolloutUnreadOverride() {
    val appServerHintsByPath = mapOf(
      "/work/project" to CodexRefreshHints(
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
        activityHintsByThreadId = mapOf(
          "thread-shared" to refreshHint(activity = AgentThreadActivity.REVIEWING, updatedAt = 210L),
          "thread-unread" to refreshHint(activity = AgentThreadActivity.UNREAD, updatedAt = 220L),
        ),
      )
    )
    val rolloutHintsByPath = mapOf(
      "/work/project" to CodexRefreshHints(
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
        activityHintsByThreadId = mapOf(
          "thread-shared" to refreshHint(activity = AgentThreadActivity.UNREAD, updatedAt = 230L),
          "thread-unread" to refreshHint(activity = AgentThreadActivity.READY, updatedAt = 200L),
          "thread-rollout-only" to refreshHint(activity = AgentThreadActivity.PROCESSING, updatedAt = 240L),
        ),
      )
    )

    val merged = mergeCodexRefreshHints(
      appServerHintsByPath = appServerHintsByPath,
      rolloutHintsByPath = rolloutHintsByPath,
    )

    val hints = merged.getValue("/work/project")
    assertThat(hints.activityHintsByThreadId.mapValues { (_, hint) -> hint.activity }).containsExactlyInAnyOrderEntriesOf(
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
  fun mergeAllowsRolloutWorkingActivityToOverrideCachedNonResponseRequiredAppServerHint() {
    val merged = mergeCodexRefreshHints(
      appServerHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-ready" to refreshHint(
              activity = AgentThreadActivity.READY,
              updatedAt = 200L,
            ),
            "thread-passive-unread" to refreshHint(
              activity = AgentThreadActivity.UNREAD,
              updatedAt = 210L,
            ),
            "thread-working" to refreshHint(
              activity = AgentThreadActivity.PROCESSING,
              updatedAt = 220L,
            ),
          )
        )
      ),
      rolloutHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-ready" to refreshHint(
              activity = AgentThreadActivity.PROCESSING,
              updatedAt = 230L,
            ),
            "thread-passive-unread" to refreshHint(
              activity = AgentThreadActivity.REVIEWING,
              updatedAt = 240L,
            ),
            "thread-working" to refreshHint(
              activity = AgentThreadActivity.REVIEWING,
              updatedAt = 250L,
            ),
          )
        )
      ),
    )

    assertThat(merged.getValue("/work/project").activityHintsByThreadId.mapValues { (_, hint) -> hint.activity })
      .containsExactlyInAnyOrderEntriesOf(
        mapOf(
          "thread-ready" to AgentThreadActivity.PROCESSING,
          "thread-passive-unread" to AgentThreadActivity.REVIEWING,
          "thread-working" to AgentThreadActivity.REVIEWING,
        )
      )
  }

  @Test
  fun mergePreservesRolloutSummaryActivityWhenRolloutOverridesActivity() {
    val merged = mergeCodexRefreshHints(
      appServerHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-with-child-activity" to refreshHint(
              activity = AgentThreadActivity.READY,
              updatedAt = 200L,
            ),
          )
        )
      ),
      rolloutHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-with-child-activity" to refreshHint(
              activity = AgentThreadActivity.UNREAD,
              updatedAt = 230L,
              summaryActivity = null,
            ),
          )
        )
      ),
    )

    val hint = merged.getValue("/work/project").activityHintsByThreadId.getValue("thread-with-child-activity")
    assertThat(hint.activity).isEqualTo(AgentThreadActivity.UNREAD)
    assertThat(hint.summaryActivity).isNull()
  }

  @Test
  fun mergeAllowsNewerRolloutWorkingActivityToOverrideStaleVerifiedAppServerHint() {
    val merged = mergeCodexRefreshHints(
      appServerHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-ready" to refreshHint(
              activity = AgentThreadActivity.READY,
              updatedAt = 200L,
              verifiedFresh = true,
            ),
            "thread-passive-unread" to refreshHint(
              activity = AgentThreadActivity.UNREAD,
              updatedAt = 210L,
              verifiedFresh = true,
            ),
            "thread-working" to refreshHint(
              activity = AgentThreadActivity.PROCESSING,
              updatedAt = 220L,
              verifiedFresh = true,
            ),
          )
        )
      ),
      rolloutHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-ready" to refreshHint(
              activity = AgentThreadActivity.PROCESSING,
              updatedAt = 230L,
            ),
            "thread-passive-unread" to refreshHint(
              activity = AgentThreadActivity.REVIEWING,
              updatedAt = 240L,
            ),
            "thread-working" to refreshHint(
              activity = AgentThreadActivity.REVIEWING,
              updatedAt = 250L,
            ),
          )
        )
      ),
    )

    assertThat(merged.getValue("/work/project").activityHintsByThreadId.mapValues { (_, hint) -> hint.activity })
      .containsExactlyInAnyOrderEntriesOf(
        mapOf(
          "thread-ready" to AgentThreadActivity.PROCESSING,
          "thread-passive-unread" to AgentThreadActivity.REVIEWING,
          "thread-working" to AgentThreadActivity.REVIEWING,
        )
      )
  }

  @Test
  fun mergeAllowsRolloutWorkingActivityToOverrideNewerVerifiedNonWorkingAppServerHint() {
    val merged = mergeCodexRefreshHints(
      appServerHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-ready" to refreshHint(
              activity = AgentThreadActivity.READY,
              updatedAt = 300L,
              verifiedFresh = true,
            ),
            "thread-passive-unread" to refreshHint(
              activity = AgentThreadActivity.UNREAD,
              updatedAt = 310L,
              verifiedFresh = true,
            ),
            "thread-working" to refreshHint(
              activity = AgentThreadActivity.PROCESSING,
              updatedAt = 320L,
              verifiedFresh = true,
            ),
          )
        )
      ),
      rolloutHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-ready" to refreshHint(
              activity = AgentThreadActivity.PROCESSING,
              updatedAt = 230L,
            ),
            "thread-passive-unread" to refreshHint(
              activity = AgentThreadActivity.REVIEWING,
              updatedAt = 240L,
            ),
            "thread-working" to refreshHint(
              activity = AgentThreadActivity.REVIEWING,
              updatedAt = 250L,
            ),
          )
        )
      ),
    )

    assertThat(merged.getValue("/work/project").activityHintsByThreadId.mapValues { (_, hint) -> hint.activity })
      .containsExactlyInAnyOrderEntriesOf(
        mapOf(
          "thread-ready" to AgentThreadActivity.PROCESSING,
          "thread-passive-unread" to AgentThreadActivity.REVIEWING,
          "thread-working" to AgentThreadActivity.PROCESSING,
        )
      )
  }

  @Test
  fun mergeAllowsNewerRolloutWorkingFallbackToOverrideStaleResponseRequiredHint() {
    val merged = mergeCodexRefreshHints(
      appServerHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.NEEDS_INPUT,
              updatedAt = 300L,
              responseRequired = true,
            )
          )
        )
      ),
      rolloutHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.PROCESSING,
              updatedAt = 320L,
            )
          )
        )
      ),
    )

    val hint = merged.getValue("/work/project").activityHintsByThreadId.getValue("thread-live")
    assertThat(hint.activity).isEqualTo(AgentThreadActivity.PROCESSING)
    assertThat(hint.responseRequired).isFalse()
    assertThat(hint.updatedAt).isEqualTo(320L)
  }

  @Test
  fun mergeAllowsNewerRolloutUnreadToOverrideStaleResponseRequiredHint() {
    val merged = mergeCodexRefreshHints(
      appServerHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.NEEDS_INPUT,
              updatedAt = 300L,
              responseRequired = true,
            )
          )
        )
      ),
      rolloutHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.UNREAD,
              updatedAt = 320L,
            )
          )
        )
      ),
    )

    val hint = merged.getValue("/work/project").activityHintsByThreadId.getValue("thread-live")
    assertThat(hint.activity).isEqualTo(AgentThreadActivity.UNREAD)
    assertThat(hint.responseRequired).isFalse()
    assertThat(hint.updatedAt).isEqualTo(320L)
  }

  @Test
  fun mergeKeepsNewerVerifiedAppServerHintWhenRolloutResponseRequiredIsStale() {
    val merged = mergeCodexRefreshHints(
      appServerHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-ready" to refreshHint(
              activity = AgentThreadActivity.READY,
              updatedAt = 300L,
              verifiedFresh = true,
            ),
            "thread-working" to refreshHint(
              activity = AgentThreadActivity.PROCESSING,
              updatedAt = 310L,
              verifiedFresh = true,
            ),
          )
        )
      ),
      rolloutHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-ready" to refreshHint(
              activity = AgentThreadActivity.NEEDS_INPUT,
              updatedAt = 200L,
              responseRequired = true,
            ),
            "thread-working" to refreshHint(
              activity = AgentThreadActivity.NEEDS_INPUT,
              updatedAt = 210L,
              responseRequired = true,
            ),
          )
        )
      ),
    )

    assertThat(merged.getValue("/work/project").activityHintsByThreadId.mapValues { (_, hint) -> hint.activity })
      .containsExactlyInAnyOrderEntriesOf(
        mapOf(
          "thread-ready" to AgentThreadActivity.READY,
          "thread-working" to AgentThreadActivity.PROCESSING,
        )
      )
  }

  @Test
  fun mergeKeepsNewerAppServerNotificationHintWhenRolloutResponseRequiredIsStale() {
    val merged = mergeCodexRefreshHints(
      appServerHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-ready" to refreshHint(
              activity = AgentThreadActivity.READY,
              updatedAt = 300L,
            )
          )
        )
      ),
      rolloutHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-ready" to refreshHint(
              activity = AgentThreadActivity.NEEDS_INPUT,
              updatedAt = 200L,
              responseRequired = true,
            )
          )
        )
      ),
    )

    val hint = merged.getValue("/work/project").activityHintsByThreadId.getValue("thread-ready")
    assertThat(hint.activity).isEqualTo(AgentThreadActivity.READY)
    assertThat(hint.responseRequired).isFalse()
    assertThat(hint.updatedAt).isEqualTo(300L)
  }

  @Test
  fun mergeKeepsCurrentResponseRequiredNeedsInputWhenRolloutWorkingFallbackIsNotNewer() {
    val merged = mergeCodexRefreshHints(
      appServerHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.NEEDS_INPUT,
              updatedAt = 300L,
              responseRequired = true,
            )
          )
        )
      ),
      rolloutHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.PROCESSING,
              updatedAt = 300L,
            )
          )
        )
      ),
    )

    val hint = merged.getValue("/work/project").activityHintsByThreadId.getValue("thread-live")
    assertThat(hint.activity).isEqualTo(AgentThreadActivity.NEEDS_INPUT)
    assertThat(hint.responseRequired).isTrue()
    assertThat(hint.updatedAt).isEqualTo(300L)
  }

  @Test
  fun mergeAllowsOlderRolloutWorkingFallbackToOverrideReadyHint() {
    val merged = mergeCodexRefreshHints(
      appServerHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.READY,
              updatedAt = 300L,
            )
          )
        )
      ),
      rolloutHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.PROCESSING,
              updatedAt = 290L,
            )
          )
        )
      ),
    )

    val hint = merged.getValue("/work/project").activityHintsByThreadId.getValue("thread-live")
    assertThat(hint.activity).isEqualTo(AgentThreadActivity.PROCESSING)
    assertThat(hint.updatedAt).isEqualTo(290L)
  }

  @Test
  fun mergeKeepsAppServerWorkingHintWhenOlderRolloutWorkingFallbackExists() {
    val merged = mergeCodexRefreshHints(
      appServerHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.PROCESSING,
              updatedAt = 300L,
            )
          )
        )
      ),
      rolloutHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.REVIEWING,
              updatedAt = 290L,
            )
          )
        )
      ),
    )

    val hint = merged.getValue("/work/project").activityHintsByThreadId.getValue("thread-live")
    assertThat(hint.activity).isEqualTo(AgentThreadActivity.PROCESSING)
    assertThat(hint.updatedAt).isEqualTo(300L)
  }

  @Test
  fun mergeAllowsNewerRolloutUnreadToOverrideStaleWorkingHint() {
    val merged = mergeCodexRefreshHints(
      appServerHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.PROCESSING,
              updatedAt = 300L,
            )
          )
        )
      ),
      rolloutHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.UNREAD,
              updatedAt = 320L,
            )
          )
        )
      ),
    )

    val hint = merged.getValue("/work/project").activityHintsByThreadId.getValue("thread-live")
    assertThat(hint.activity).isEqualTo(AgentThreadActivity.UNREAD)
    assertThat(hint.updatedAt).isEqualTo(320L)
  }

  @Test
  fun mergeKeepsAppServerWorkingHintWhenOlderRolloutUnreadExists() {
    val merged = mergeCodexRefreshHints(
      appServerHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.PROCESSING,
              updatedAt = 300L,
            )
          )
        )
      ),
      rolloutHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.UNREAD,
              updatedAt = 290L,
            )
          )
        )
      ),
    )

    val hint = merged.getValue("/work/project").activityHintsByThreadId.getValue("thread-live")
    assertThat(hint.activity).isEqualTo(AgentThreadActivity.PROCESSING)
    assertThat(hint.updatedAt).isEqualTo(300L)
  }

  @Test
  fun mergeAllowsNewerRolloutResponseRequiredNeedsInputToOverrideReadyHint() {
    val merged = mergeCodexRefreshHints(
      appServerHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.READY,
              updatedAt = 300L,
            )
          )
        )
      ),
      rolloutHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.NEEDS_INPUT,
              updatedAt = 320L,
              responseRequired = true,
            )
          )
        )
      ),
    )

    val hint = merged.getValue("/work/project").activityHintsByThreadId.getValue("thread-live")
    assertThat(hint.activity).isEqualTo(AgentThreadActivity.NEEDS_INPUT)
    assertThat(hint.responseRequired).isTrue()
    assertThat(hint.updatedAt).isEqualTo(320L)
  }

  @Test
  fun mergeAllowsNewerRolloutReadyToClearStaleAppServerWorkingState() {
    val merged = mergeCodexRefreshHints(
      appServerHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.PROCESSING,
              updatedAt = 300L,
            )
          )
        )
      ),
      rolloutHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.READY,
              updatedAt = 320L,
            )
          )
        )
      ),
    )

    val hint = merged.getValue("/work/project").activityHintsByThreadId.getValue("thread-live")
    assertThat(hint.activity).isEqualTo(AgentThreadActivity.READY)
    assertThat(hint.updatedAt).isEqualTo(320L)
  }

  @Test
  fun mergeKeepsAppServerWorkingHintWhenOlderRolloutReadyExists() {
    val merged = mergeCodexRefreshHints(
      appServerHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.PROCESSING,
              updatedAt = 300L,
            )
          )
        )
      ),
      rolloutHintsByPath = mapOf(
        "/work/project" to CodexRefreshHints(
          activityHintsByThreadId = mapOf(
            "thread-live" to refreshHint(
              activity = AgentThreadActivity.READY,
              updatedAt = 290L,
            )
          )
        )
      ),
    )

    val hint = merged.getValue("/work/project").activityHintsByThreadId.getValue("thread-live")
    assertThat(hint.activity).isEqualTo(AgentThreadActivity.PROCESSING)
    assertThat(hint.updatedAt).isEqualTo(300L)
  }

  @Test
  fun mergeReturnsNonEmptySourceWhenOtherIsEmpty() {
    val hints = mapOf(
      "/work/project" to CodexRefreshHints(
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

private fun refreshHint(
  activity: AgentThreadActivity,
  updatedAt: Long,
  responseRequired: Boolean = false,
  verifiedFresh: Boolean = false,
  summaryActivity: AgentThreadActivity? = activity,
): CodexRefreshActivityHint {
  return CodexRefreshActivityHint(
    activity = activity,
    updatedAt = updatedAt,
    responseRequired = responseRequired,
    verifiedFresh = verifiedFresh,
    summaryActivity = summaryActivity,
  )
}
