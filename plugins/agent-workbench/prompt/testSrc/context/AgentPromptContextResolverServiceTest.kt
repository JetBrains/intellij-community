// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextContributorBridge
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextContributorPhase
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentPromptContextResolverServiceTest {
  @Test
  fun invocationPhaseUsesFirstNonEmptyContributor() {
    var firstInvocationCalls = 0
    var secondInvocationCalls = 0
    var fallbackCalls = 0

    val service = AgentPromptContextResolverService(
      contributorsProvider = {
        listOf(
          testContributor(contributorPhase = AgentPromptContextContributorPhase.INVOCATION, contributorOrder = 0) {
            firstInvocationCalls++
            emptyList()
          },
          testContributor(contributorPhase = AgentPromptContextContributorPhase.INVOCATION, contributorOrder = 1) {
            secondInvocationCalls++
            listOf(contextItem(AgentPromptContextRendererIds.SNIPPET))
          },
          testContributor(contributorPhase = AgentPromptContextContributorPhase.FALLBACK, contributorOrder = 0) {
            fallbackCalls++
            listOf(contextItem(AgentPromptContextRendererIds.PATHS))
          },
        )
      }
    )

    val resolved = service.collectDefaultContext(invocationData())

    assertThat(resolved.map { it.rendererId }).containsExactly(AgentPromptContextRendererIds.SNIPPET)
    assertThat(resolved.single().phase).isEqualTo(AgentPromptContextContributorPhase.INVOCATION)
    assertThat(firstInvocationCalls).isEqualTo(1)
    assertThat(secondInvocationCalls).isEqualTo(1)
    assertThat(fallbackCalls).isZero()
  }

  @Test
  fun fallbackPhaseIsUsedWhenInvocationContributorsAreEmpty() {
    var fallbackCalls = 0

    val service = AgentPromptContextResolverService(
      contributorsProvider = {
        listOf(
          testContributor(contributorPhase = AgentPromptContextContributorPhase.INVOCATION, contributorOrder = 0) { emptyList() },
          testContributor(contributorPhase = AgentPromptContextContributorPhase.FALLBACK, contributorOrder = 0) {
            fallbackCalls++
            listOf(contextItem(AgentPromptContextRendererIds.PATHS))
          },
        )
      }
    )

    val resolved = service.collectDefaultContext(invocationData())

    assertThat(resolved.map { it.rendererId }).containsExactly(AgentPromptContextRendererIds.PATHS)
    assertThat(resolved.single().phase).isEqualTo(AgentPromptContextContributorPhase.FALLBACK)
    assertThat(fallbackCalls).isEqualTo(1)
  }

  @Test
  fun failingContributorIsIgnoredAndResolutionContinues() {
    val service = AgentPromptContextResolverService(
      contributorsProvider = {
        listOf(
          testContributor(contributorPhase = AgentPromptContextContributorPhase.INVOCATION, contributorOrder = 0) {
            error("boom")
          },
          testContributor(contributorPhase = AgentPromptContextContributorPhase.INVOCATION, contributorOrder = 1) {
            listOf(contextItem(AgentPromptContextRendererIds.FILE))
          },
        )
      }
    )

    val resolved = service.collectDefaultContext(invocationData())

    assertThat(resolved.map { it.rendererId }).containsExactly(AgentPromptContextRendererIds.FILE)
    assertThat(resolved.single().phase).isEqualTo(AgentPromptContextContributorPhase.INVOCATION)
  }

  private fun invocationData(): AgentPromptInvocationData {
    val project = ProjectManager.getInstance().defaultProject
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .build()
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

  private fun contextItem(rendererId: String): AgentPromptContextItem {
    return AgentPromptContextItem(
      rendererId = rendererId,
      title = "Context",
      body = "Value",
      source = "test",
    )
  }

  private fun testContributor(
    contributorPhase: AgentPromptContextContributorPhase,
    contributorOrder: Int,
    collector: (AgentPromptInvocationData) -> List<AgentPromptContextItem>,
  ): AgentPromptContextContributorBridge {
    return object : AgentPromptContextContributorBridge {
      override val phase: AgentPromptContextContributorPhase
        get() = contributorPhase

      override val order: Int
        get() = contributorOrder

      override fun collect(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem> {
        return collector(invocationData)
      }
    }
  }
}
