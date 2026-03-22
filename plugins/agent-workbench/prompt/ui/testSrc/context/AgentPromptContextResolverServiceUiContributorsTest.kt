// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context

import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY
import com.intellij.agent.workbench.prompt.core.AgentPromptContextContributorBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptContextContributorPhase
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextResolverService
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentPromptContextResolverServiceUiContributorsTest {
  @Test
  fun vcsCommitsWinOverProjectSelectionWhenBothArePresent() {
    val project = ProjectManager.getInstance().defaultProject
    val expectedHashes = listOf("a1b2c3d4", "e5f6g7h8")
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.VIRTUAL_FILE, LightVirtualFile("selected.txt", ""))
      .build()
    val service = AgentPromptContextResolverService(
      contributorsProvider = {
        listOf(
          AgentPromptEditorContextContributor(),
          testContributor(contributorOrder = 50) {
            listOf(contextItem(AgentPromptContextRendererIds.VCS_COMMITS, expectedHashes.joinToString(separator = "\n")))
          },
          AgentPromptProjectViewSelectionContextContributor(),
          AgentPromptSelectedEditorFallbackContextContributor(),
        )
      }
    )

    val resolved = service.collectDefaultContext(invocationData(dataContext))

    assertThat(resolved).hasSize(1)
    assertThat(resolved.single().rendererId).isEqualTo(AgentPromptContextRendererIds.VCS_COMMITS)
    assertThat(resolved.single().body.lineSequence().toList()).containsExactlyElementsOf(expectedHashes)
  }

  @Test
  fun testFailuresWinOverVcsAndProjectSelectionWhenBothArePresent() {
    val project = ProjectManager.getInstance().defaultProject
    val expectedTestLine = "failed: java:test://Suite.testFailed"
    val expectedVcsHash = "deadbeef"
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.VIRTUAL_FILE, LightVirtualFile("selected.txt", ""))
      .build()
    val service = AgentPromptContextResolverService(
      contributorsProvider = {
        listOf(
          AgentPromptEditorContextContributor(),
          testContributor(contributorOrder = 40) {
            listOf(contextItem(AgentPromptContextRendererIds.TEST_FAILURES, expectedTestLine))
          },
          testContributor(contributorOrder = 50) {
            listOf(contextItem(AgentPromptContextRendererIds.VCS_COMMITS, expectedVcsHash))
          },
          AgentPromptProjectViewSelectionContextContributor(),
          AgentPromptSelectedEditorFallbackContextContributor(),
        )
      }
    )

    val resolved = service.collectDefaultContext(invocationData(dataContext))

    assertThat(resolved).hasSize(1)
    assertThat(resolved.single().rendererId).isEqualTo(AgentPromptContextRendererIds.TEST_FAILURES)
    assertThat(resolved.single().body).isEqualTo(expectedTestLine)
  }

  private fun invocationData(dataContext: DataContext): AgentPromptInvocationData {
    val project = ProjectManager.getInstance().defaultProject
    return AgentPromptInvocationData(
      project = project,
      actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
      actionText = "Ask Agent",
      actionPlace = "MainMenu",
      invokedAtMs = 0L,
      attributes = mapOf(
        AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY to dataContext,
      ),
    )
  }

  private fun contextItem(rendererId: String, body: String): AgentPromptContextItem {
    return AgentPromptContextItem(
      rendererId = rendererId,
      title = "Context",
      body = body,
      source = "test",
    )
  }

  private fun testContributor(
    contributorOrder: Int,
    collector: (AgentPromptInvocationData) -> List<AgentPromptContextItem>,
  ): AgentPromptContextContributorBridge {
    return object : AgentPromptContextContributorBridge {
      override val phase: AgentPromptContextContributorPhase
        get() = AgentPromptContextContributorPhase.INVOCATION

      override val order: Int
        get() = contributorOrder

      override fun collect(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem> {
        return collector(invocationData)
      }
    }
  }
}
