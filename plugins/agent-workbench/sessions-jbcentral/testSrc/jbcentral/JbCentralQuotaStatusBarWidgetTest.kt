// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.settings.AGENT_WORKBENCH_STATUS_BAR_WIDGETS_SETTINGS_COMPONENT_ID
import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class JbCentralQuotaStatusBarWidgetTest {
  @Test
  fun widgetIsDisabledByDefault() {
    assertThat(JbCentralQuotaStatusBarWidgetFactory().isEnabledByDefault).isFalse()
  }

  @Test
  fun settingsContributorTogglesJbCentralQuotaWidget(@TestDisposable disposable: Disposable) {
    registerJbCentralQuotaWidgetFactoryIfMissing(disposable)

    val initialEnabled = JbCentralQuotaStatusBarWidgetSettings.isEnabled()
    try {
      JbCentralQuotaStatusBarWidgetSettings.setEnabled(false)

      val component = JbCentralQuotaSettingsContributor().components().single()
      assertThat(component.id).isEqualTo(AGENT_WORKBENCH_STATUS_BAR_WIDGETS_SETTINGS_COMPONENT_ID)
      assertThat(component.displayName).isEqualTo(AgentSessionsBundle.message("settings.agent.workbench.status.bar.widgets.group"))

      val setting = component.checkboxSettings.single()
      assertThat(setting.text).isEqualTo(AgentSessionsBundle.message("settings.agent.workbench.jbcentral.quota.status.bar.widget"))
      assertThat(setting.description).isEqualTo(AgentSessionsBundle.message("settings.agent.workbench.jbcentral.quota.status.bar.widget.description"))
      assertThat(setting.isSelected()).isFalse()

      setting.setSelected(true)

      assertThat(JbCentralQuotaStatusBarWidgetSettings.isEnabled()).isTrue()
    }
    finally {
      JbCentralQuotaStatusBarWidgetSettings.setEnabled(initialEnabled)
    }
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

  private fun registerJbCentralQuotaWidgetFactoryIfMissing(disposable: Disposable) {
    if (StatusBarWidgetFactory.EP_NAME.extensionList.none { it.id == JBCENTRAL_QUOTA_WIDGET_ID }) {
      StatusBarWidgetFactory.EP_NAME.point.registerExtension(JbCentralQuotaStatusBarWidgetFactory(), disposable)
    }
  }
}
