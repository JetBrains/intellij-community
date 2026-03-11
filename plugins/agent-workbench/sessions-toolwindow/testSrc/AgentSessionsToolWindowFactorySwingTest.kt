// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.toolwindow.ui.dispatchTreeRowOverlayQuickCreate
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsToolWindowFactorySwingTest {
  @Test
  fun descriptorPointsToolWindowToSwingFactoryWithoutComposeEntries() {
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.agent.workbench.sessions.toolwindow.xml")) {
      "Module descriptor intellij.agent.workbench.sessions.toolwindow.xml is missing"
    }.readText()

    assertThat(descriptor)
      .contains("factoryClass=\"com.intellij.agent.workbench.sessions.toolwindow.ui.AgentSessionsToolWindowFactory\"")
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
    assertThat(action?.javaClass?.name)
      .isEqualTo("com.intellij.agent.workbench.sessions.actions.AgentSessionsOpenDedicatedFrameAction")
    assertThat(action?.templatePresentation?.icon).isEqualTo(AllIcons.Actions.MoveToWindow)
  }

  @Test
  fun descriptorRegistersTreePopupActions() {
    val actionManager = ActionManager.getInstance()

    assertThat(actionManager.childActionIds("AgentWorkbenchSessions.TreePopup"))
      .contains("AgentWorkbenchSessions.TreePopup.Open")
      .contains("AgentWorkbenchSessions.TreePopup.More")
      .contains("AgentWorkbenchSessions.TreePopup.NewThread")
      .contains("AgentWorkbenchSessions.TreePopup.Archive")
      .contains("CopyReferencePopupGroup")

    assertThat(actionManager.getAction("AgentWorkbenchSessions.TreePopup.NewThread"))
      .isNotNull
    assertThat(actionManager.getAction("AgentWorkbenchSessions.TreePopup.NewThread")?.templatePresentation?.icon)
      .isEqualTo(AllIcons.General.Add)
  }

  @Test
  fun treeRowOverlayQuickCreateUsesOverlayEntryPoint() {
    val project = ProjectManager.getInstance().defaultProject
    var capturedPath: String? = null
    var capturedProvider: AgentSessionProvider? = null
    var capturedMode: AgentSessionLaunchMode? = null
    var capturedEntryPoint: AgentWorkbenchEntryPoint? = null
    var capturedProject = false

    dispatchTreeRowOverlayQuickCreate(
      project = project,
      path = "/work/project",
      provider = AgentSessionProvider.CODEX,
      createNewSession = { path, provider, mode, entryPoint, currentProject ->
        capturedPath = path
        capturedProvider = provider
        capturedMode = mode
        capturedEntryPoint = entryPoint
        capturedProject = currentProject === project
      },
    )

    assertThat(capturedPath).isEqualTo("/work/project")
    assertThat(capturedProvider).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(capturedMode).isEqualTo(AgentSessionLaunchMode.STANDARD)
    assertThat(capturedEntryPoint).isEqualTo(AgentWorkbenchEntryPoint.TREE_ROW_OVERLAY)
    assertThat(capturedProject).isTrue()
  }

  private fun ActionManager.childActionIds(groupId: String): List<String> {
    val group = getAction(groupId) as? ActionGroup
    assertThat(group).withFailMessage("Action group '%s' is not registered", groupId).isNotNull
    return checkNotNull(group).getChildren(TestActionEvent.createTestEvent()).mapNotNull { getId(it) }
  }
}
