// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.actions.AgentSessionsSwitchSourceAndChatAction
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionsSwitchSourceAndChatActionTest {
  @Test
  fun sourceProjectFocusesActiveChat() {
    var focusedProjectName: String? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
    val action = AgentSessionsSwitchSourceAndChatAction(
      isDedicatedProject = { false },
      selectedSourceProjectPath = { null },
      switchSourceAndChat = { project, capturedEntryPoint ->
        focusedProjectName = project.name
        entryPoint = capturedEntryPoint
        true
      },
    )
    val event = testEventWithProject(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.description)
      .isEqualTo(AgentSessionsBundle.message("action.AgentWorkbenchSessions.SwitchSourceAndChat.to.chat.description"))

    action.actionPerformed(event)

    assertThat(focusedProjectName).isEqualTo(event.project?.name)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.WINDOW_MENU)
  }

  @Test
  fun dedicatedFrameWithSourceProjectFocusesSourceProject() {
    var sourceProjectName: String? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
    val sourcePath = "/work/source-project"
    val action = AgentSessionsSwitchSourceAndChatAction(
      isDedicatedProject = { true },
      selectedSourceProjectPath = { sourcePath },
      switchSourceAndChat = { project, capturedEntryPoint ->
        sourceProjectName = project.name
        entryPoint = capturedEntryPoint
        true
      },
    )
    val event = testEventWithProject(action)

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()
    assertThat(event.presentation.description)
      .isEqualTo(AgentSessionsBundle.message("action.AgentWorkbenchSessions.SwitchSourceAndChat.to.source.description", sourcePath))

    action.actionPerformed(event)

    assertThat(sourceProjectName).isEqualTo(event.project?.name)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.WINDOW_MENU)
  }

  @Test
  fun dedicatedFrameWithoutSourceProjectIsDisabled() {
    var invocations = 0
    val action = AgentSessionsSwitchSourceAndChatAction(
      isDedicatedProject = { true },
      selectedSourceProjectPath = { null },
      switchSourceAndChat = { _, _ ->
        invocations++
        true
      },
    )
    val event = testEventWithProject(action)

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.description)
      .isEqualTo(AgentSessionsBundle.message("action.AgentWorkbenchSessions.SwitchSourceAndChat.empty.description"))

    action.actionPerformed(event)

    assertThat(invocations).isZero()
  }

  @Test
  fun hiddenWithoutProjectContext() {
    var invocations = 0
    val action = AgentSessionsSwitchSourceAndChatAction(
      switchSourceAndChat = { _, _ ->
        invocations++
        true
      },
    )
    val event = TestActionEvent.createTestEvent(action, SimpleDataContext.builder().build())

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()

    action.actionPerformed(event)

    assertThat(invocations).isZero()
  }
}

private fun testEventWithProject(action: AnAction) =
  TestActionEvent.createTestEvent(
    action,
    SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, ProjectManager.getInstance().defaultProject)
      .build(),
  )
