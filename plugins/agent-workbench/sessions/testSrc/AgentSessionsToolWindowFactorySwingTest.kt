// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsToolWindowFactorySwingTest {
  @Test
  fun descriptorPointsToolWindowToSwingFactoryWithoutComposeEntries() {
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.agent.workbench.sessions.xml")) {
      "Module descriptor intellij.agent.workbench.sessions.xml is missing"
    }.readText()

    assertThat(descriptor)
      .contains("factoryClass=\"com.intellij.agent.workbench.sessions.AgentSessionsToolWindowFactory\"")
      .doesNotContain("Compose")
      .doesNotContain("compose")
  }

  @Test
  fun descriptorRegistersGearActionsGroup() {
    val actionManager = ActionManager.getInstance()

    assertThat(actionManager.childActionIds("AgentWorkbenchSessions.ToolWindow.GearActions"))
      .contains("OpenFile")
      .contains("AgentWorkbenchSessions.ToggleDedicatedFrame")
      .contains("AgentWorkbenchSessions.ToggleClaudeQuotaWidget")
      .contains("AgentWorkbenchSessions.Refresh")
      .doesNotContain("AgentWorkbenchSessions.OpenDedicatedFrame")
  }

  @Test
  fun descriptorRegistersOpenDedicatedFrameHeaderAction() {
    val actionManager = ActionManager.getInstance()
    val action = actionManager.getAction("AgentWorkbenchSessions.OpenDedicatedFrame")

    assertThat(action)
      .isNotNull
      .isInstanceOf(AgentSessionsOpenDedicatedFrameAction::class.java)
    assertThat(action?.templatePresentation?.icon).isEqualTo(AllIcons.Actions.MoveToWindow)
  }

  private fun ActionManager.childActionIds(groupId: String): List<String> {
    val group = getAction(groupId) as? ActionGroup
    assertThat(group).withFailMessage("Action group '%s' is not registered", groupId).isNotNull
    return checkNotNull(group).getChildren(TestActionEvent.createTestEvent()).mapNotNull { getId(it) }
  }
}
