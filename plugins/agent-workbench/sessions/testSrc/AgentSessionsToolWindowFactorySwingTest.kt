// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.actions.AgentSessionsOpenDedicatedFrameAction
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
      .contains("factoryClass=\"com.intellij.agent.workbench.sessions.ui.AgentSessionsToolWindowFactory\"")
      .doesNotContain("Compose")
      .doesNotContain("compose")
  }

  @Test
  fun descriptorRegistersGearActionsGroup() {
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.agent.workbench.sessions.xml")) {
      "Module descriptor intellij.agent.workbench.sessions.xml is missing"
    }.readText()

    assertThat(descriptor)
      .contains("<group id=\"AgentWorkbenchSessions.ToolWindow.GearActions\">")
      .contains("<action id=\"AgentWorkbenchSessions.Refresh\"")
      .contains("<action id=\"AgentWorkbenchSessions.ToggleDedicatedFrame\"")
  }
}
