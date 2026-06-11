// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.sessions.core.settings.AGENT_WORKBENCH_STATUS_BAR_WIDGETS_SETTINGS_COMPONENT_ID
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettingsContributors
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Separator
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionsClaudeQuotaWidgetActionRegistrationTest {
  @Test
  fun gearActionsContainClaudeQuotaWidgetToggle() {
    val actionManager = ActionManager.getInstance()

    assertThat(actionManager.getAction("AgentWorkbenchSessions.ToggleClaudeQuotaWidget"))
      .isNotNull
      .isInstanceOf(AgentSessionsToggleClaudeQuotaWidgetAction::class.java)
    assertThat(actionManager.childActionEntries("AgentWorkbenchSessions.ToolWindow.GearActions"))
      .containsSubsequence(
        "AgentWorkbenchSessions.Refresh",
        ACTION_SEPARATOR_MARKER,
        "AgentWorkbenchSessions.ToggleClaudeQuotaWidget",
        "AgentWorkbenchSessions.ToggleDedicatedFrame",
      )
  }

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

  private fun ActionManager.childActionEntries(groupId: String): List<String> {
    val group = getAction(groupId) as? ActionGroup
    assertThat(group).withFailMessage("Action group '%s' is not registered", groupId).isNotNull
    return flattenEntries(checkNotNull(group).getChildren(TestActionEvent.createTestEvent()))
  }

  private fun ActionManager.flattenEntries(actions: Array<AnAction>): List<String> {
    return actions.mapNotNull { action ->
      when (action) {
        is Separator -> ACTION_SEPARATOR_MARKER
        else -> getId(action)
      }
    }
  }

  companion object {
    private const val ACTION_SEPARATOR_MARKER = "<separator>"
  }
}
