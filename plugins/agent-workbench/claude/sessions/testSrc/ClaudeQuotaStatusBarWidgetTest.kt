// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class ClaudeQuotaStatusBarWidgetTest {
  @Test
  fun widgetIsDisabledByDefault() {
    assertThat(ClaudeQuotaStatusBarWidgetFactory().isEnabledByDefault).isFalse()
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
