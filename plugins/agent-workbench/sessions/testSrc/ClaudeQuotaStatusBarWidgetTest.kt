// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.claude.ClaudeQuotaInfo
import com.intellij.agent.workbench.sessions.claude.ClaudeQuotaStatusBarWidgetFactory
import com.intellij.agent.workbench.sessions.claude.dominantPercent
import com.intellij.agent.workbench.sessions.claude.formatQuotaResetTime
import com.intellij.agent.workbench.sessions.claude.formatWidgetText
import com.intellij.agent.workbench.sessions.claude.formatWidgetTooltip
import com.intellij.agent.workbench.sessions.claude.isWarningQuota
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ClaudeQuotaStatusBarWidgetTest {
  @Test
  fun widgetIsDisabledByDefault() {
    assertThat(ClaudeQuotaStatusBarWidgetFactory().isEnabledByDefault).isFalse()
  }

  @Test
  fun formatTextShowsSessionWhenHigher() {
    val info = ClaudeQuotaInfo(fiveHourPercent = 45, fiveHourReset = null, sevenDayPercent = 12, sevenDayReset = null)
    val text = formatWidgetText(info)
    assertThat(text).isEqualTo(AgentSessionsBundle.message("status.bar.claude.quota.text", 45, "5h"))
  }

  @Test
  fun formatTextShowsWeeklyWhenHigher() {
    val info = ClaudeQuotaInfo(fiveHourPercent = 10, fiveHourReset = null, sevenDayPercent = 80, sevenDayReset = null)
    val text = formatWidgetText(info)
    assertThat(text).isEqualTo(AgentSessionsBundle.message("status.bar.claude.quota.text", 80, "7d"))
  }

  @Test
  fun formatTextShowsSessionOnly() {
    val info = ClaudeQuotaInfo(fiveHourPercent = 50, fiveHourReset = null, sevenDayPercent = null, sevenDayReset = null)
    val text = formatWidgetText(info)
    assertThat(text).isEqualTo(AgentSessionsBundle.message("status.bar.claude.quota.text", 50, "5h"))
  }

  @Test
  fun formatTextShowsWeeklyOnly() {
    val info = ClaudeQuotaInfo(fiveHourPercent = null, fiveHourReset = null, sevenDayPercent = 30, sevenDayReset = null)
    val text = formatWidgetText(info)
    assertThat(text).isEqualTo(AgentSessionsBundle.message("status.bar.claude.quota.text", 30, "7d"))
  }

  @Test
  fun formatTextReturnsEmptyWhenAllNull() {
    val info = ClaudeQuotaInfo(fiveHourPercent = null, fiveHourReset = null, sevenDayPercent = null, sevenDayReset = null)
    val text = formatWidgetText(info)
    assertThat(text).isEmpty()
  }

  @Test
  fun formatTooltipShowsBothLines() {
    val now = 1_700_000_000_000L
    val info = ClaudeQuotaInfo(
      fiveHourPercent = 45,
      fiveHourReset = now + 2 * 60 * 60 * 1000L,
      sevenDayPercent = 12,
      sevenDayReset = now + 5 * 24 * 60 * 60 * 1000L,
    )
    val tooltip = formatWidgetTooltip(info, now)
    assertThat(tooltip).startsWith("<html>")
    assertThat(tooltip).endsWith("</html>")
    assertThat(tooltip).contains("<br>")
    assertThat(tooltip).contains("45%")
    assertThat(tooltip).contains("12%")
    assertThat(tooltip).contains("2h 0m")
    assertThat(tooltip).contains("5d 0h")
  }

  @Test
  fun formatTooltipShowsSessionOnly() {
    val now = 1_700_000_000_000L
    val info = ClaudeQuotaInfo(
      fiveHourPercent = 80,
      fiveHourReset = now + 30 * 60 * 1000L,
      sevenDayPercent = null,
      sevenDayReset = null,
    )
    val tooltip = formatWidgetTooltip(info, now)
    assertThat(tooltip).doesNotContain("<html>")
    assertThat(tooltip).contains("80%")
    assertThat(tooltip).contains("30m")
    assertThat(tooltip).doesNotContain("Weekly")
  }

  @Test
  fun dominantPercentReturnsHigherOfTwo() {
    val info = ClaudeQuotaInfo(fiveHourPercent = 30, fiveHourReset = null, sevenDayPercent = 70, sevenDayReset = null)
    assertThat(dominantPercent(info)).isEqualTo(70)
  }

  @Test
  fun dominantPercentReturnsSessionWhenWeeklyNull() {
    val info = ClaudeQuotaInfo(fiveHourPercent = 50, fiveHourReset = null, sevenDayPercent = null, sevenDayReset = null)
    assertThat(dominantPercent(info)).isEqualTo(50)
  }

  @Test
  fun dominantPercentReturnsNullWhenBothNull() {
    val info = ClaudeQuotaInfo(fiveHourPercent = null, fiveHourReset = null, sevenDayPercent = null, sevenDayReset = null)
    assertThat(dominantPercent(info)).isNull()
  }

  @Test
  fun warningQuotaOnlyAboveEightyPercent() {
    assertThat(isWarningQuota(80)).isFalse()
    assertThat(isWarningQuota(81)).isTrue()
  }

  @Test
  fun formatQuotaResetTimeShowsMinutesOnly() {
    val now = 1_700_000_000_000L
    assertThat(formatQuotaResetTime(now + 30 * 60 * 1000L, now)).isEqualTo("30m")
    assertThat(formatQuotaResetTime(now + 1 * 60 * 1000L, now)).isEqualTo("1m")
    assertThat(formatQuotaResetTime(now + 59 * 60 * 1000L, now)).isEqualTo("59m")
  }

  @Test
  fun formatQuotaResetTimeShowsHoursAndMinutes() {
    val now = 1_700_000_000_000L
    assertThat(formatQuotaResetTime(now + 2 * 60 * 60 * 1000L, now)).isEqualTo("2h 0m")
    assertThat(formatQuotaResetTime(now + (2 * 60 + 15) * 60 * 1000L, now)).isEqualTo("2h 15m")
    assertThat(formatQuotaResetTime(now + (4 * 60 + 45) * 60 * 1000L, now)).isEqualTo("4h 45m")
  }

  @Test
  fun formatQuotaResetTimeShowsDaysAndHours() {
    val now = 1_700_000_000_000L
    assertThat(formatQuotaResetTime(now + 5 * 24 * 60 * 60 * 1000L, now)).isEqualTo("5d 0h")
    assertThat(formatQuotaResetTime(now + (2 * 24 * 60 + 3 * 60 + 24) * 60 * 1000L, now)).isEqualTo("2d 3h")
  }

  @Test
  fun formatQuotaResetTimeShowsZeroMinutesWhenPast() {
    val now = 1_700_000_000_000L
    assertThat(formatQuotaResetTime(now - 5 * 60 * 1000L, now)).isEqualTo("0m")
  }
}
