// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Separator
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
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
