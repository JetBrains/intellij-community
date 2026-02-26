// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsOpenDedicatedFrameActionTest {
  @Test
  fun visibleInNonDedicatedProjectAndInvokesOpenCallback() {
    var openedProjectName: String? = null
    val action = AgentSessionsOpenDedicatedFrameAction(
      isDedicatedProject = { false },
      openDedicatedFrame = { project -> openedProjectName = project.name },
    )
    val event = testEventWithProject(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()

    action.actionPerformed(event)

    assertThat(openedProjectName).isEqualTo(event.project?.name)
  }

  @Test
  fun hiddenInDedicatedProjectAndDoesNotInvokeOpenCallback() {
    var invocations = 0
    val action = AgentSessionsOpenDedicatedFrameAction(
      isDedicatedProject = { true },
      openDedicatedFrame = { _ -> invocations++ },
    )
    val event = testEventWithProject(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()

    action.actionPerformed(event)

    assertThat(invocations).isZero()
  }

  @Test
  fun hiddenWithoutProjectContext() {
    var invocations = 0
    val action = AgentSessionsOpenDedicatedFrameAction(
      isDedicatedProject = { false },
      openDedicatedFrame = { _ -> invocations++ },
    )
    val dataContext = SimpleDataContext.builder().build()
    val event = TestActionEvent.createTestEvent(action, dataContext)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()

    action.actionPerformed(event)

    assertThat(invocations).isZero()
  }
}

private fun testEventWithProject(action: AgentSessionsOpenDedicatedFrameAction) =
  TestActionEvent.createTestEvent(
    action,
    SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, ProjectManager.getInstance().defaultProject)
      .build(),
  )
