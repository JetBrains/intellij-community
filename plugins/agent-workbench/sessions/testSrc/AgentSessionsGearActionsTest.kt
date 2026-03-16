// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsGearActionsTest {
  @Test
  fun gearActionsContainOpenFileToggleAndRefresh() {
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.agent.workbench.sessions.xml")) {
      "Module descriptor intellij.agent.workbench.sessions.xml is missing"
    }.readText()

    assertThat(descriptor)
      .contains("<group id=\"AgentWorkbenchSessions.ToolWindow.GearActions\">")
      .contains("<reference ref=\"OpenFile\"/>")
      .contains("<action id=\"AgentWorkbenchSessions.ToggleDedicatedFrame\"")
      .contains("<action id=\"AgentWorkbenchSessions.Refresh\"")
  }

  @Test
  fun toggleActionUpdatesAdvancedSetting() {
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.agent.workbench.sessions.xml")) {
      "Module descriptor intellij.agent.workbench.sessions.xml is missing"
    }.readText()

    assertThat(descriptor)
      .contains("<advancedSetting")
      .contains("id=\"agent.workbench.chat.open.in.dedicated.frame\"")
      .contains("<action id=\"AgentWorkbenchSessions.ToggleDedicatedFrame\"")
  }

  @Test
  fun dedicatedFrameRegistersProjectShortcutAliasAction() {
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.agent.workbench.sessions.xml")) {
      "Module descriptor intellij.agent.workbench.sessions.xml is missing"
    }.readText()

    assertThat(descriptor)
      .contains("<action")
      .contains("id=\"AgentWorkbenchSessions.ActivateWithProjectShortcut\"")
      .contains("use-shortcut-of=\"ActivateProjectToolWindow\"")
      .contains("class=\"com.intellij.agent.workbench.sessions.AgentSessionsActivateWithProjectShortcutAction\"")
      .doesNotContain("id=\"ActivateProjectToolWindow\"")
  }

  @Test
  fun refreshActionTriggersSessionsRefresh() {
    val refreshAction = AgentSessionsRefreshAction()

    val service = ApplicationManager.getApplication().getService(AgentSessionsService::class.java)
    val initialTimestamp = service.state.value.lastUpdatedAt

    runInEdtAndWait {
      refreshAction.actionPerformed(TestActionEvent.createTestEvent(refreshAction))
    }

    val timeoutAt = System.currentTimeMillis() + 5_000
    var refreshed = false
    while (System.currentTimeMillis() < timeoutAt) {
      val updatedAt = service.state.value.lastUpdatedAt
      if (updatedAt != null && updatedAt != initialTimestamp) {
        refreshed = true
        break
      }
      Thread.sleep(20)
    }
    assertThat(refreshed)
      .withFailMessage("Refresh action didn't trigger a sessions state update in time.")
      .isTrue()
  }
}
