// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentChatEditorTabTitleProviderTest {
  private val project = ProjectManager.getInstance().defaultProject

  @Test
  fun dedicatedFrameTooltipIncludesSourceProjectPath() {
    val provider = titleProvider(isDedicatedProject = { true })

    val tooltip = provider.getEditorTabTooltipHtml(project, chatFile())?.toString()

    assertThat(tooltip)
      .contains("Fix auth")
      .contains("Source project: /work/project-a")
  }

  @Test
  fun regularProjectTooltipUsesTitleOnly() {
    val provider = titleProvider(isDedicatedProject = { false })

    val tooltip = provider.getEditorTabTooltipHtml(project, chatFile())?.toString()

    assertThat(tooltip).isEqualTo("Fix auth")
  }

  @Test
  fun blankSourceProjectPathTooltipUsesTitleOnly() {
    val provider = titleProvider(isDedicatedProject = { true })

    val tooltip = provider.getEditorTabTooltipHtml(project, chatFile(projectPath = ""))?.toString()

    assertThat(tooltip).isEqualTo("Fix auth")
  }

  private fun titleProvider(isDedicatedProject: (Project) -> Boolean): AgentChatEditorTabTitleProvider {
    return AgentChatEditorTabTitleProvider(isDedicatedProject = isDedicatedProject)
  }

  private fun chatFile(projectPath: String = "/work/project-a"): AgentChatVirtualFile {
    return AgentChatVirtualFile(
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
