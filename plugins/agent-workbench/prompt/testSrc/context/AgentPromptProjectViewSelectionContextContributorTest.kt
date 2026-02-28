// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentPromptProjectViewSelectionContextContributorTest {
  private val contributor = AgentPromptProjectViewSelectionContextContributor()

  @Test
  fun aggregatesAndTruncatesSelectionToConfiguredLimit() {
    val selected = (1..7).map { index -> LightVirtualFile("File$index.kt", "fun f$index() = $index") }
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, selected.toTypedArray())
      .build()

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    assertThat(item.kindId).isEqualTo(AgentPromptContextKinds.PATHS)
    assertThat(item.metadata["selectedCount"]).isEqualTo("7")
    assertThat(item.metadata["includedCount"]).isEqualTo("5")
    assertThat(item.metadata["truncated"]).isEqualTo("true")
    assertThat(item.content.lineSequence().toList()).hasSize(5)
    assertThat(item.content.lineSequence().all { line -> line.startsWith("file: ") }).isTrue()
  }

  @Test
  fun usesSingleVirtualFileSelectionWhenArrayIsMissing() {
    val selected = LightVirtualFile("README.md", "# readme")
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.VIRTUAL_FILE, selected)
      .build()

    val result = contributor.collect(invocationData(dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    assertThat(item.kindId).isEqualTo(AgentPromptContextKinds.PATHS)
    assertThat(item.metadata["selectedCount"]).isEqualTo("1")
    assertThat(item.metadata["includedCount"]).isEqualTo("1")
    assertThat(item.metadata["truncated"]).isEqualTo("false")
    assertThat(item.metadata["fileCount"]).isEqualTo("1")
    assertThat(item.metadata["directoryCount"]).isEqualTo("0")
    assertThat(item.content).contains("file: ")
  }

  private fun invocationData(dataContext: com.intellij.openapi.actionSystem.DataContext): AgentPromptInvocationData {
    val project = ProjectManager.getInstance().defaultProject
    return AgentPromptInvocationData(
      project = project,
      actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
      actionText = "Ask Agent",
      actionPlace = "ProjectViewPopup",
      invokedAtMs = 0L,
      attributes = mapOf(
        AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY to dataContext,
      ),
    )
  }
}
