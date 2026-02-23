// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.AgentThreadQuickStartService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentChatNewThreadFromEditorTabActionTest {
  @Test
  fun actionIsVisibleAndEnabledWhenQuickStartIsAvailable() {
    val context = editorContext()
    val quickStart = TestQuickStartService(
      isVisible = true,
      isEnabled = true,
    )
    val action = AgentChatNewThreadFromEditorTabAction(
      resolveContext = { context },
      quickStartService = { quickStart },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun actionIsHiddenWithoutChatContext() {
    val action = AgentChatNewThreadFromEditorTabAction(
      resolveContext = { null },
      quickStartService = { TestQuickStartService(isVisible = true, isEnabled = true) },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun actionIsHiddenWhenQuickStartIsUnavailableInProject() {
    val context = editorContext()
    val action = AgentChatNewThreadFromEditorTabAction(
      resolveContext = { context },
      quickStartService = { TestQuickStartService(isVisible = false, isEnabled = false) },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun actionPerformsOnlyWhenQuickStartIsVisibleAndEnabled() {
    val context = editorContext()
    val quickStart = TestQuickStartService(
      isVisible = true,
      isEnabled = true,
    )
    val action = AgentChatNewThreadFromEditorTabAction(
      resolveContext = { context },
      quickStartService = { quickStart },
    )

    action.actionPerformed(TestActionEvent.createTestEvent(action))

    assertThat(quickStart.launchedPath).isEqualTo(context.path)
    assertThat(quickStart.launchedProject).isSameAs(context.project)
  }

  @Test
  fun moduleDescriptorRegistersEditorTabNewThreadAction() {
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.agent.workbench.chat.xml")) {
      "Module descriptor intellij.agent.workbench.chat.xml is missing"
    }.readText()

    assertThat(descriptor)
      .contains("id=\"AgentWorkbenchChat.NewThreadFromEditorTab\"")
      .contains("class=\"com.intellij.agent.workbench.chat.AgentChatNewThreadFromEditorTabAction\"")
      .contains("icon=\"AllIcons.General.Add\"")
  }
}

private fun editorContext(): AgentChatEditorTabActionContext {
  return AgentChatEditorTabActionContext(
    project = ProjectManager.getInstance().defaultProject,
    path = normalizeAgentWorkbenchPath("/tmp/project"),
  )
}

private class TestQuickStartService(
  private val isVisible: Boolean,
  private val isEnabled: Boolean,
) : AgentThreadQuickStartService {
  var launchedPath: String? = null
  var launchedProject: Project? = null

  override fun isVisible(project: Project): Boolean {
    return isVisible
  }

  override fun isEnabled(project: Project): Boolean {
    return isEnabled
  }

  override fun startNewThread(path: String, project: Project) {
    launchedPath = path
    launchedProject = project
  }
}
