// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentWorkbenchManageLaunchProfilesActionTest {
  @Test
  fun hidesWithoutProject() {
    val action = AgentWorkbenchManageLaunchProfilesAction {}
    val event = event(action, project = null)

    action.update(event)

    assertThat(event.presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun invokesProfileManagerWithProject() {
    var openedProject: Project? = null
    val action = AgentWorkbenchManageLaunchProfilesAction { project -> openedProject = project }
    val event = event(action, project = ProjectManager.getInstance().defaultProject)

    action.update(event)
    action.actionPerformed(event)

    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(openedProject).isSameAs(ProjectManager.getInstance().defaultProject)
  }

  private fun event(action: AgentWorkbenchManageLaunchProfilesAction, project: Project?): AnActionEvent {
    val dataContext = SimpleDataContext.builder()
      .apply {
        if (project != null) {
          add(CommonDataKeys.PROJECT, project)
        }
      }
      .build()
    return AnActionEvent.createEvent(action, dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.POPUP, null)
  }
}
