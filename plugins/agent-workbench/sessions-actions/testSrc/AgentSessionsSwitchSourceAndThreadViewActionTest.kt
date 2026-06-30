// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.actions.AgentSessionsSwitchSourceAndAgentThreadViewAction
import com.intellij.agent.workbench.sessions.statistics.AgentWorkbenchEntryPoint
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
class AgentSessionsSwitchSourceAndAgentThreadViewActionTest {
  @Test
  fun sourceProjectFocusesActiveThreadView() {
    var focusedProjectName: String? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
    val action = AgentSessionsSwitchSourceAndAgentThreadViewAction(
      isDedicatedProject = { false },
      selectedSourceProjectPath = { null },
      switchSourceAndThreadView = { project, capturedEntryPoint ->
        focusedProjectName = project.name
        entryPoint = capturedEntryPoint
        true
      },
    )
    val event = testEventWithProject(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.description)
      .isEqualTo(AgentSessionsBundle.message("action.AgentWorkbenchSessions.SwitchSourceAndAgentThreadView.to.thread.view.description"))

    action.actionPerformed(event)

    assertThat(focusedProjectName).isEqualTo(event.project?.name)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.WINDOW_MENU)
  }

  @Test
  fun dedicatedFrameWithSourceProjectFocusesSourceProject() {
    var sourceProjectName: String? = null
    var entryPoint: AgentWorkbenchEntryPoint? = null
    val sourcePath = "/work/source-project"
    val action = AgentSessionsSwitchSourceAndAgentThreadViewAction(
      isDedicatedProject = { true },
      selectedSourceProjectPath = { sourcePath },
      switchSourceAndThreadView = { project, capturedEntryPoint ->
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
      .isEqualTo(AgentSessionsBundle.message("action.AgentWorkbenchSessions.SwitchSourceAndAgentThreadView.to.source.description", sourcePath))

    action.actionPerformed(event)

    assertThat(sourceProjectName).isEqualTo(event.project?.name)
    assertThat(entryPoint).isEqualTo(AgentWorkbenchEntryPoint.WINDOW_MENU)
  }

  @Test
  fun dedicatedFrameWithoutSourceProjectIsDisabled() {
    var invocations = 0
    val action = AgentSessionsSwitchSourceAndAgentThreadViewAction(
      isDedicatedProject = { true },
      selectedSourceProjectPath = { null },
      switchSourceAndThreadView = { _, _ ->
        invocations++
        true
      },
    )
    val event = testEventWithProject(action)

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.description)
      .isEqualTo(AgentSessionsBundle.message("action.AgentWorkbenchSessions.SwitchSourceAndAgentThreadView.empty.description"))

    action.actionPerformed(event)

    assertThat(invocations).isZero()
  }

  @Test
  fun hiddenWithoutProjectContext() {
    var invocations = 0
    val action = AgentSessionsSwitchSourceAndAgentThreadViewAction(
      switchSourceAndThreadView = { _, _ ->
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
