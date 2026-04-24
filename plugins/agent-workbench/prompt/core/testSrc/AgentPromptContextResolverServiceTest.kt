// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

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
          testContributor(contributorPhase = AgentPromptContextContributorPhase.INVOCATION) {
            firstInvocationCalls++
            emptyList()
          },
          testContributor(contributorPhase = AgentPromptContextContributorPhase.INVOCATION) {
            secondInvocationCalls++
            listOf(contextItem(AgentPromptContextRendererIds.SNIPPET))
          },
          testContributor(contributorPhase = AgentPromptContextContributorPhase.FALLBACK) {
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
          testContributor(contributorPhase = AgentPromptContextContributorPhase.INVOCATION) { emptyList() },
          testContributor(contributorPhase = AgentPromptContextContributorPhase.FALLBACK) {
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
          testContributor(contributorPhase = AgentPromptContextContributorPhase.INVOCATION) {
            error("boom")
          },
          testContributor(contributorPhase = AgentPromptContextContributorPhase.INVOCATION) {
            listOf(contextItem(AgentPromptContextRendererIds.FILE))
          },
        )
      }
    )

    val resolved = service.collectDefaultContext(invocationData())

    assertThat(resolved.map { it.rendererId }).containsExactly(AgentPromptContextRendererIds.FILE)
    assertThat(resolved.single().phase).isEqualTo(AgentPromptContextContributorPhase.INVOCATION)
  }

  @Test
  fun registrationOrderWinsOverClassNameSortingWithinPhase() {
    val service = AgentPromptContextResolverService(
      contributorsProvider = {
        listOf(
          ZuluInvocationContributor(contextItem(AgentPromptContextRendererIds.FILE)),
          AlphaInvocationContributor(contextItem(AgentPromptContextRendererIds.SNIPPET)),
        )
      }
    )

    val resolved = service.collectDefaultContext(invocationData())

    assertThat(resolved.map { it.rendererId }).containsExactly(AgentPromptContextRendererIds.FILE)
  }

  private fun invocationData(dataContext: com.intellij.openapi.actionSystem.DataContext? = null): AgentPromptInvocationData {
    val project = ProjectManager.getInstance().defaultProject
    val effectiveDataContext = dataContext ?: SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .build()
    return AgentPromptInvocationData(
      project = project,
      actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
      actionText = "Ask Agent",
      actionPlace = "MainMenu",
      invokedAtMs = 0L,
      attributes = mapOf(
        AGENT_PROMPT_INVOCATION_DATA_CONTEXT_KEY to effectiveDataContext,
      ),
    )
  }

  private fun contextItem(rendererId: String, body: String = "Value"): AgentPromptContextItem {
    return AgentPromptContextItem(
      rendererId = rendererId,
      title = "Context",
      body = body,
      source = "test",
    )
  }

  private fun testContributor(
    contributorPhase: AgentPromptContextContributorPhase,
    collector: (AgentPromptInvocationData) -> List<AgentPromptContextItem>,
  ): AgentPromptContextContributorBridge {
    return object : AgentPromptContextContributorBridge {
      override val phase: AgentPromptContextContributorPhase
        get() = contributorPhase

      override fun collect(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem> {
        return collector(invocationData)
      }
    }
  }

  private class AlphaInvocationContributor(
    private val item: AgentPromptContextItem,
  ) : AgentPromptContextContributorBridge {
    override fun collect(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem> = listOf(item)
  }

  private class ZuluInvocationContributor(
    private val item: AgentPromptContextItem,
  ) : AgentPromptContextContributorBridge {
    override fun collect(invocationData: AgentPromptInvocationData): List<AgentPromptContextItem> = listOf(item)
  }
}
