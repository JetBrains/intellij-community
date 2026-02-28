// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.formatAgentSessionRelativeTimeShort
import com.intellij.agent.workbench.sessions.core.formatAgentSessionThreadTitle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentSessionThreadPresentationTest {
  @Test
  fun titleNormalizationCollapsesWhitespaceAndTrims() {
    val result = formatAgentSessionThreadTitle(
      threadId = "thread-1234",
      title = "\n  Hello   from\tAgent\r  ",
      fallbackTitle = { "Thread $it" },
    )

    assertThat(result).isEqualTo("Hello from Agent")
  }

  @Test
  fun titleNormalizationUsesFallbackForBlankTitles() {
    val result = formatAgentSessionThreadTitle(
      threadId = "abcdef123456",
      title = "\n  \t  ",
      fallbackTitle = { "Thread $it" },
    )

    assertThat(result).isEqualTo("Thread abcdef12")
  }

  @Test
  fun titleNormalizationUsesUnknownIdFallbackWhenThreadIdBlank() {
    val result = formatAgentSessionThreadTitle(
      threadId = "   ",
      title = "",
      fallbackTitle = { "Thread $it" },
    )

    assertThat(result).isEqualTo("Thread unknown")
  }

  @Test
  fun relativeTimeReturnsUnknownLabelForMissingTimestamp() {
    val result = formatAgentSessionRelativeTimeShort(
      timestamp = 0L,
      now = 10_000L,
      nowLabel = "now",
      unknownLabel = "—",
    )

    assertThat(result).isEqualTo("—")
  }

  @Test
  fun relativeTimeUsesNowLabelForRecentUpdates() {
    val result = formatAgentSessionRelativeTimeShort(
      timestamp = 10_000L,
      now = 69_000L,
      nowLabel = "now",
      unknownLabel = "—",
    )

    assertThat(result).isEqualTo("now")
  }

  @Test
  fun relativeTimeUsesRoundedBucketsAcrossUnits() {
    val now = 10L * 365L * 24L * 60L * 60L * 1000L

    val minutes = formatAgentSessionRelativeTimeShort(
      timestamp = now - 90_000L,
      now = now,
      nowLabel = "now",
      unknownLabel = "—",
    )
    val hours = formatAgentSessionRelativeTimeShort(
      timestamp = now - (2 * 60 * 60 * 1000L),
      now = now,
      nowLabel = "now",
      unknownLabel = "—",
    )
    val days = formatAgentSessionRelativeTimeShort(
      timestamp = now - (3 * 24 * 60 * 60 * 1000L),
      now = now,
      nowLabel = "now",
      unknownLabel = "—",
    )
    val weeks = formatAgentSessionRelativeTimeShort(
      timestamp = now - (14 * 24 * 60 * 60 * 1000L),
      now = now,
      nowLabel = "now",
      unknownLabel = "—",
    )
    val months = formatAgentSessionRelativeTimeShort(
      timestamp = now - (60 * 24 * 60 * 60 * 1000L),
      now = now,
      nowLabel = "now",
      unknownLabel = "—",
    )
    val years = formatAgentSessionRelativeTimeShort(
      timestamp = now - (2 * 365 * 24 * 60 * 60 * 1000L),
      now = now,
      nowLabel = "now",
      unknownLabel = "—",
    )

    assertThat(minutes).isEqualTo("2m")
    assertThat(hours).isEqualTo("2h")
    assertThat(days).isEqualTo("3d")
    assertThat(weeks).isEqualTo("2w")
    assertThat(months).isEqualTo("2mo")
    assertThat(years).isEqualTo("2y")
  }
}
