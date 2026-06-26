// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.claude.sessions

import com.intellij.agent.workbench.settings.AGENT_WORKBENCH_STATUS_BAR_WIDGETS_SETTINGS_COMPONENT_ID
import com.intellij.agent.workbench.settings.AgentWorkbenchSettingsContributors
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionsClaudeQuotaWidgetActionRegistrationTest {
  @Test
  fun settingsContributorTogglesClaudeQuotaWidget() {
    assertThat(AgentWorkbenchSettingsContributors.all()).anyMatch { it is ClaudeQuotaSettingsContributor }

    val initialEnabled = ClaudeQuotaStatusBarWidgetSettings.isEnabled()
    try {
      ClaudeQuotaStatusBarWidgetSettings.setEnabled(false)

      val component = ClaudeQuotaSettingsContributor().components().single()
      assertThat(component.id).isEqualTo(AGENT_WORKBENCH_STATUS_BAR_WIDGETS_SETTINGS_COMPONENT_ID)
      assertThat(component.displayName).isEqualTo(ClaudeSessionsBundle.message("settings.agent.workbench.status.bar.widgets.group"))

      val setting = component.checkboxSettings.single()
      assertThat(setting.text).isEqualTo(ClaudeSessionsBundle.message("settings.agent.workbench.claude.quota.status.bar.widget"))
      assertThat(setting.description).isEqualTo(ClaudeSessionsBundle.message("settings.agent.workbench.claude.quota.status.bar.widget.description"))
      assertThat(setting.isSelected()).isFalse()

      setting.setSelected(true)

      assertThat(ClaudeQuotaStatusBarWidgetSettings.isEnabled()).isTrue()
    }
    finally {
      ClaudeQuotaStatusBarWidgetSettings.setEnabled(initialEnabled)
    }
  }
}
