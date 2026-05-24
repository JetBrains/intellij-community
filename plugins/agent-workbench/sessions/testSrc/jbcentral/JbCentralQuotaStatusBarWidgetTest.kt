// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JbCentralQuotaStatusBarWidgetTest {
  @Test
  fun widgetIsDisabledByDefault() {
    assertThat(JbCentralQuotaStatusBarWidgetFactory().isEnabledByDefault).isFalse()
  }

  @Test
  fun formatTextShowsRemainingQuota() {
    val info = sampleInfo()

    assertThat(formatJbCentralQuotaText(info)).isEqualTo("$198.52")
  }

  @Test
  fun remainingPercentUsesInverseOfUsedPercent() {
    assertThat(remainingPercent(sampleInfo(percentUsed = 0.7))).isEqualTo(99)
    assertThat(remainingPercent(sampleInfo(percentUsed = 81.4))).isEqualTo(19)
    assertThat(remainingPercent(sampleInfo(percentUsed = null))).isEqualTo(99)
  }

  @Test
  fun formatTooltipIncludesUserLicenseAndResetDate() {
    val tooltip = formatJbCentralQuotaTooltip(sampleInfo())

    assertThat(tooltip).startsWith("<html>")
    assertThat(tooltip).contains("Ivan.Kuleshov@jetbrains.com")
    assertThat(tooltip).contains("JetBrains AI Ultimate")
    assertThat(tooltip).contains("$198.52")
    assertThat(tooltip).contains("$200.00")
    assertThat(tooltip).contains("0.7%")
    assertThat(tooltip).contains("Jun 1, 2026")
  }

  @Test
  fun lowQuotaWarningStartsBelowTwentyPercentRemaining() {
    assertThat(isLowRemainingQuota(20)).isFalse()
    assertThat(isLowRemainingQuota(19)).isTrue()
  }

  private fun sampleInfo(percentUsed: Double? = 0.7): JbCentralQuotaInfo {
    return JbCentralQuotaInfo(
      email = "Ivan.Kuleshov@jetbrains.com",
      licenseName = "JetBrains AI Ultimate",
      usedUsd = "1.48",
      totalUsd = "200.00",
      remainingUsd = "198.52",
      percentUsed = percentUsed,
      resetDateText = "Jun 1, 2026",
    )
  }
}
