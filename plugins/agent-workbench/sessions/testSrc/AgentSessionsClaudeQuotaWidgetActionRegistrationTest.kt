// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.actions.AgentSessionsToggleClaudeQuotaWidgetAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
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
    assertThat(actionManager.childActionIds("AgentWorkbenchSessions.ToolWindow.GearActions"))
      .contains("AgentWorkbenchSessions.ToggleClaudeQuotaWidget")
  }

  private fun ActionManager.childActionIds(groupId: String): List<String> {
    val group = getAction(groupId) as? ActionGroup
    assertThat(group).withFailMessage("Action group '%s' is not registered", groupId).isNotNull
    return checkNotNull(group).getChildren(TestActionEvent.createTestEvent()).mapNotNull { getId(it) }
  }
}
