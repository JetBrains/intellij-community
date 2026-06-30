// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentThreadViewEditorTabTitleProviderTest {
  private val project = ProjectManager.getInstance().defaultProject

  @Test
  fun dedicatedFrameTooltipIncludesSourceProjectPath() {
    val provider = titleProvider(isDedicatedProject = { true })

    val tooltip = provider.getEditorTabTooltipHtml(project, threadViewFile())?.toString()

    assertThat(tooltip)
      .contains("Fix auth")
      .contains("Source project: /work/project-a")
  }

  @Test
  fun regularProjectTooltipUsesTitleOnly() {
    val provider = titleProvider(isDedicatedProject = { false })

    val tooltip = provider.getEditorTabTooltipHtml(project, threadViewFile())?.toString()

    assertThat(tooltip).isEqualTo("Fix auth")
  }

  @Test
  fun blankSourceProjectPathTooltipUsesTitleOnly() {
    val provider = titleProvider(isDedicatedProject = { true })

    val tooltip = provider.getEditorTabTooltipHtml(project, threadViewFile(projectPath = ""))?.toString()

    assertThat(tooltip).isEqualTo("Fix auth")
  }

  private fun titleProvider(isDedicatedProject: (Project) -> Boolean): AgentThreadViewEditorTabTitleProvider {
    return AgentThreadViewEditorTabTitleProvider(isDedicatedProject = isDedicatedProject)
  }

  private fun threadViewFile(projectPath: String = "/work/project-a"): AgentThreadViewVirtualFile {
    return AgentThreadViewVirtualFile(
      projectPath = projectPath,
      threadIdentity = "CODEX:thread-1",
      shellCommand = emptyList(),
      threadId = "thread-1",
      threadTitle = "Fix auth",
      subAgentId = null,
      threadActivity = AgentThreadActivity.READY,
    )
  }
}
