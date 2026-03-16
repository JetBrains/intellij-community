// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class AgentSessionsClaudeQuotaWidgetActionRegistrationTest {
  @Test
  fun gearActionsContainClaudeQuotaWidgetToggle() {
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.agent.workbench.sessions.xml")) {
      "Module descriptor intellij.agent.workbench.sessions.xml is missing"
    }.readText()

    assertThat(descriptor)
      .contains("<group id=\"AgentWorkbenchSessions.ToolWindow.GearActions\">")
      .contains("<action id=\"AgentWorkbenchSessions.ToggleClaudeQuotaWidget\"")
  }
}
