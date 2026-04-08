// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentPromptSuggestionSeedsTest {
  @Test
  fun failingTestsProduceFixFirstSuggestions() {
    val suggestions = AgentPromptSuggestionSeeds.buildDefaultSuggestions(
      listOf(
        contextItem(
          rendererId = AgentPromptContextRendererIds.TEST_FAILURES,
          payload = AgentPromptPayload.obj(
            "statusCounts" to AgentPromptPayload.obj(
              "failed" to AgentPromptPayload.num(2),
              "passed" to AgentPromptPayload.num(1),
            )
          )
        )
      )
    )

    assertThat(suggestions.map(AgentPromptSuggestionCandidate::id))
      .containsExactly("tests.fix", "tests.explain", "tests.stabilize")
  }

  @Test
  fun passingTestsProduceCoverageSuggestions() {
    val suggestions = AgentPromptSuggestionSeeds.buildDefaultSuggestions(
      listOf(
        contextItem(
          rendererId = AgentPromptContextRendererIds.TEST_FAILURES,
          payload = AgentPromptPayload.obj(
            "statusCounts" to AgentPromptPayload.obj(
              "passed" to AgentPromptPayload.num(1),
              "ignored" to AgentPromptPayload.num(1),
            )
          )
        )
      )
    )

    assertThat(suggestions.map(AgentPromptSuggestionCandidate::id))
      .containsExactly("tests.coverage", "tests.review", "tests.extend")
  }

  @Test
  fun vcsContextProducesCommitSuggestions() {
    val suggestions = AgentPromptSuggestionSeeds.buildDefaultSuggestions(
      listOf(contextItem(rendererId = AgentPromptContextRendererIds.VCS_COMMITS))
    )

    assertThat(suggestions.map(AgentPromptSuggestionCandidate::id))
      .containsExactly("vcs.review", "vcs.summary", "vcs.trace")
  }

  @Test
  fun editorContextProducesCodeSuggestions() {
    val suggestions = AgentPromptSuggestionSeeds.buildDefaultSuggestions(
      listOf(contextItem(rendererId = AgentPromptContextRendererIds.SNIPPET))
    )

    assertThat(suggestions.map(AgentPromptSuggestionCandidate::id))
      .containsExactly("editor.explain", "editor.refactor", "editor.review")
  }

  @Test
  fun pathsContextProducesFileSuggestions() {
    val suggestions = AgentPromptSuggestionSeeds.buildDefaultSuggestions(
      listOf(contextItem(rendererId = AgentPromptContextRendererIds.PATHS))
    )

    assertThat(suggestions.map(AgentPromptSuggestionCandidate::id))
      .containsExactly("paths.plan", "paths.summary", "paths.impact")
  }

  @Test
  fun testContextOutranksVcsContext() {
    val suggestions = AgentPromptSuggestionSeeds.buildDefaultSuggestions(
      listOf(
        contextItem(rendererId = AgentPromptContextRendererIds.VCS_COMMITS),
        contextItem(
          rendererId = AgentPromptContextRendererIds.TEST_FAILURES,
          payload = AgentPromptPayload.obj(
            "statusCounts" to AgentPromptPayload.obj("failed" to AgentPromptPayload.num(1))
          )
        ),
      )
    )

    assertThat(suggestions.map(AgentPromptSuggestionCandidate::id))
      .containsExactly("tests.fix", "tests.explain", "tests.stabilize")
  }

  private fun contextItem(
    rendererId: String,
    payload: com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue = AgentPromptPayload.obj(),
  ): AgentPromptContextItem {
    return AgentPromptContextItem(
      rendererId = rendererId,
      title = rendererId,
      body = "body",
      payload = payload,
    )
  }
}
