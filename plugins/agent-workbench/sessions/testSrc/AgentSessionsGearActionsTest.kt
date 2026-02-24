// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

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
  fun editorTabActionsAreRegisteredInEditorTabPopupMenu() {
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.agent.workbench.sessions.xml")) {
      "Module descriptor intellij.agent.workbench.sessions.xml is missing"
    }.readText()

    assertThat(descriptor)
      .contains("id=\"AgentWorkbenchSessions.SelectThreadInAgentThreads\"")
      .contains("id=\"AgentWorkbenchSessions.ArchiveThreadFromEditorTab\"")
      .contains("id=\"AgentWorkbenchSessions.CopyThreadIdFromEditorTab\"")
      .contains("class=\"com.intellij.agent.workbench.sessions.AgentSessionsArchiveThreadAction\"")
      .contains("group-id=\"EditorTabPopupMenu\"")
      .contains("anchor=\"before\" relative-to-action=\"CloseEditorsGroup\"")
      .contains("anchor=\"after\" relative-to-action=\"CloseEditorsGroup\"")
      .contains("anchor=\"after\" relative-to-action=\"AgentWorkbenchSessions.SelectThreadInAgentThreads\"")
      .contains("keymap=\"" + '$' + "default\" first-keystroke=\"control alt F4\"")
      .contains("keymap=\"Mac OS X 10.5+\" first-keystroke=\"meta alt W\"")
  }

  @Test
  fun treePopupActionsAreRegisteredInActionSystem() {
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.agent.workbench.sessions.xml")) {
      "Module descriptor intellij.agent.workbench.sessions.xml is missing"
    }.readText()

    assertThat(descriptor)
      .contains("<group id=\"AgentWorkbenchSessions.TreePopup\">")
      .contains("<reference ref=\"AgentWorkbenchSessions.TreePopup.Open\"/>")
      .contains("<reference ref=\"AgentWorkbenchSessions.TreePopup.More\"/>")
      .contains("<reference ref=\"AgentWorkbenchSessions.TreePopup.NewThread\"/>")
      .contains("<reference ref=\"AgentWorkbenchSessions.TreePopup.Archive\"/>")
      .contains("id=\"AgentWorkbenchSessions.TreePopup.Open\"")
      .contains("id=\"AgentWorkbenchSessions.TreePopup.More\"")
      .contains("id=\"AgentWorkbenchSessions.TreePopup.NewThread\"")
      .contains("id=\"AgentWorkbenchSessions.TreePopup.Archive\"")
  }

  @Test
  fun refreshActionTriggersSessionsRefresh() {
    var refreshInvocations = 0
    val refreshAction = AgentSessionsRefreshAction(
      refreshSessions = { refreshInvocations++ },
      isRefreshingProvider = { false },
    )

    runInEdtAndWait {
      refreshAction.actionPerformed(TestActionEvent.createTestEvent(refreshAction))
    }

    assertThat(refreshInvocations)
      .withFailMessage("Refresh action didn't trigger sessions refresh callback.")
      .isEqualTo(1)
  }
}
