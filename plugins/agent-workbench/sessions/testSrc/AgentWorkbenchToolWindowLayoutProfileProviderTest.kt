// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.frame.AGENT_WORKBENCH_DEDICATED_LAYOUT_PROFILE_ID
import com.intellij.agent.workbench.sessions.frame.AGENT_WORKBENCH_LAYOUT_MIGRATION_VERSION
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchToolWindowLayoutProfileProvider
import com.intellij.agent.workbench.sessions.frame.TERMINAL_TOOL_WINDOW_ID
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.testFramework.ApplicationRule
import com.intellij.toolWindow.ToolWindowLayoutApplyMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

class AgentWorkbenchToolWindowLayoutProfileProviderTest {
  @get:Rule
  val applicationRule = ApplicationRule()

  private val provider = AgentWorkbenchToolWindowLayoutProfileProvider()

  @Test
  fun dedicatedProfileUsesForceOnceMigrationPolicy() {
    val project = testProject()

    assertThat(provider.getApplyMode(project, AGENT_WORKBENCH_DEDICATED_LAYOUT_PROFILE_ID, isNewUi = true))
      .isEqualTo(ToolWindowLayoutApplyMode.FORCE_ONCE)
    assertThat(provider.getMigrationVersion(project, AGENT_WORKBENCH_DEDICATED_LAYOUT_PROFILE_ID, isNewUi = true))
      .isEqualTo(AGENT_WORKBENCH_LAYOUT_MIGRATION_VERSION)
  }

  @Test
  fun dedicatedLayoutHidesStructureAndKeepsTerminalInStripeWithoutOpeningIt() {
    val project = testProject()

    val layout = provider.getLayout(project, AGENT_WORKBENCH_DEDICATED_LAYOUT_PROFILE_ID, isNewUi = true)

    assertThat(layout).isNotNull
    assertThat(layout!!.getInfo(ToolWindowId.PROJECT_VIEW)).isNull()
    assertThat(layout.getInfo(ToolWindowId.STRUCTURE_VIEW)).isNull()

    val terminalInfo = layout.getInfo(TERMINAL_TOOL_WINDOW_ID)
    assertThat(terminalInfo).isNotNull
    assertThat(terminalInfo!!.anchor).isEqualTo(ToolWindowAnchor.BOTTOM)
    assertThat(terminalInfo.isShowStripeButton).isTrue()
    assertThat(terminalInfo.isVisible).isFalse()
  }

  @Test
  fun nonDedicatedProfileKeepsSeedOnlyPolicy() {
    val project = testProject()

    assertThat(provider.getApplyMode(project, "other.profile", isNewUi = true))
      .isEqualTo(ToolWindowLayoutApplyMode.SEED_ONLY)
    assertThat(provider.getMigrationVersion(project, "other.profile", isNewUi = true))
      .isEqualTo(0)
  }
}

private fun testProject(): Project {
  val handler = java.lang.reflect.InvocationHandler { _, method, _ ->
    when (method.name) {
      "isDisposed" -> false
      else -> null
    }
  }
  return Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java), handler) as Project
}
