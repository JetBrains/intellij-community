// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.ide.ui.ProductIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsGoToSourceProjectFromToolbarActionTest {
  @Test
  fun dedicatedFrameWithSourceProjectIsVisibleAndOpensProject() {
    val sourcePath = "/tmp/source-project"
    var openedPath: String? = null
    val action = AgentSessionsGoToSourceProjectFromToolbarAction(
      selectedSourcePath = { sourcePath },
      isDedicatedProject = { true },
      openProject = { path -> openedPath = path },
    )
    val event = testEventWithProject(action)

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()
    assertThat(event.presentation.text).isEqualTo("source-project")
    assertThat(event.presentation.description)
      .isEqualTo(AgentSessionsBundle.message("action.AgentWorkbenchSessions.GoToSourceProjectFromToolbar.description", normalizeAgentWorkbenchPath(sourcePath)))
    assertThat(event.presentation.icon).isEqualTo(ProductIcons.getInstance().getProjectNodeIcon())

    action.actionPerformed(event)

    assertThat(openedPath).isEqualTo(normalizeAgentWorkbenchPath(sourcePath))
  }

  @Test
  fun hiddenOutsideDedicatedFrame() {
    var openCalls = 0
    val action = AgentSessionsGoToSourceProjectFromToolbarAction(
      selectedSourcePath = { "/tmp/source-project" },
      isDedicatedProject = { false },
      openProject = { _ -> openCalls++ },
    )
    val event = testEventWithProject(action)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()

    action.actionPerformed(event)

    assertThat(openCalls).isZero()
  }

  @Test
  fun dedicatedFrameWithoutSourceProjectShowsDisabledPlaceholder() {
    var openCalls = 0
    val action = AgentSessionsGoToSourceProjectFromToolbarAction(
      selectedSourcePath = { null },
      isDedicatedProject = { true },
      openProject = { _ -> openCalls++ },
    )
    val event = testEventWithProject(action)

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.text)
      .isEqualTo(AgentSessionsBundle.message("action.AgentWorkbenchSessions.GoToSourceProjectFromToolbar.empty.text"))
    assertThat(event.presentation.description)
      .isEqualTo(AgentSessionsBundle.message("action.AgentWorkbenchSessions.GoToSourceProjectFromToolbar.empty.description"))
    assertThat(event.presentation.icon).isEqualTo(ProductIcons.getInstance().getProjectNodeIcon())

    action.actionPerformed(event)

    assertThat(openCalls).isZero()
  }

  @Test
  fun dedicatedFramePathIsNotOpenable() {
    var openCalls = 0
    val action = AgentSessionsGoToSourceProjectFromToolbarAction(
      selectedSourcePath = { AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath() },
      isDedicatedProject = { true },
      openProject = { _ -> openCalls++ },
    )
    val event = testEventWithProject(action)

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()

    action.actionPerformed(event)

    assertThat(openCalls).isZero()
  }
}

private fun testEventWithProject(action: AnAction) =
  TestActionEvent.createTestEvent(
    action,
    SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, ProjectManager.getInstance().defaultProject)
      .build(),
  )
