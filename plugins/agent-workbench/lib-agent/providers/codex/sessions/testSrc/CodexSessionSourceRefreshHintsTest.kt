// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions

import com.intellij.platform.ai.agent.codex.sessions.backend.CodexRefreshActivityHint
import com.intellij.platform.ai.agent.codex.sessions.backend.CodexRefreshHints
import com.intellij.platform.ai.agent.codex.sessions.backend.toAgentSessionRefreshHints
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionThreadPresentationUpdate
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
        "thread-a" to CodexRefreshActivityHint(activity = AgentThreadActivity.PROCESSING, updatedAt = 200L)
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
  fun conversionCanCarryChromeIndependentActivityHints() {
    val hints = CodexRefreshHints(
      activityHintsByThreadId = mapOf(
        "thread-a" to CodexRefreshActivityHint(
          activity = AgentThreadActivity.NEEDS_INPUT,
          updatedAt = 300L,
          responseRequired = true,
          summaryActivity = null,
          hasSummaryActivityHint = false,
        )
      ),
    ).toAgentSessionRefreshHints()

    val update = hints.activityUpdatesByThreadId.getValue("thread-a")
    assertThat(update.activityReport).isEqualTo(
      AgentThreadActivityReport(rowActivity = AgentThreadActivity.NEEDS_INPUT, chromeActivity = null)
    )
    assertThat(update.updatesChromeActivity).isFalse()
    assertThat(update.updatedAt).isEqualTo(300L)
  }
}
