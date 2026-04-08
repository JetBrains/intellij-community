// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentPromptSuggestionGeneratorTest {
  @Test
  fun defaultGeneratorEmitsFallbackSuggestionsBeforePolishedAiUpdate() {
    val updates = AgentPromptSuggestionAiBackends.withBackendForTest(
      AgentPromptSuggestionAiBackend { _, fallbackCandidates ->
        AgentPromptSuggestionUpdate(
          fallbackCandidates.map { candidate ->
            candidate.copy(
              label = "AI ${candidate.label}",
              promptText = "${candidate.promptText} Keep the answer concise.",
              provenance = AgentPromptSuggestionProvenance.AI_POLISHED,
            )
          }
        )
      }
    ) {
      runBlocking(Dispatchers.Default) {
        checkNotNull(AgentPromptSuggestionGenerators.find())
          .generateSuggestions(failedTestRequest())
          .toList()
      }
    }

    assertThat(updates).hasSize(2)
    assertThat(updates.first().candidates.map(AgentPromptSuggestionCandidate::id))
      .containsExactly("tests.fix", "tests.explain", "tests.stabilize")
    assertThat(updates.first().candidates.map(AgentPromptSuggestionCandidate::provenance))
      .containsOnly(AgentPromptSuggestionProvenance.TEMPLATE)
    assertThat(updates.last().candidates.map(AgentPromptSuggestionCandidate::id))
      .containsExactly("tests.fix", "tests.explain", "tests.stabilize")
    assertThat(updates.last().candidates.map(AgentPromptSuggestionCandidate::provenance))
      .containsOnly(AgentPromptSuggestionProvenance.AI_POLISHED)
  }

  @Test
  fun defaultGeneratorAllowsAiGeneratedSuggestionsWithoutFallbackSeeds() {
    val updates = AgentPromptSuggestionAiBackends.withBackendForTest(
      AgentPromptSuggestionAiBackend { _, _ ->
        AgentPromptSuggestionUpdate(
          listOf(
            AgentPromptSuggestionCandidate(
              id = "context.generated.summary.1",
              label = "Summarize the custom context",
              promptText = "Summarize the custom context and highlight what needs attention.",
              provenance = AgentPromptSuggestionProvenance.AI_GENERATED,
            )
          )
        )
      }
    ) {
      runBlocking(Dispatchers.Default) {
        checkNotNull(AgentPromptSuggestionGenerators.find())
          .generateSuggestions(customContextRequest())
          .toList()
      }
    }

    assertThat(updates).hasSize(2)
    assertThat(updates.first().candidates).isEmpty()
    assertThat(updates.last().candidates.single().provenance).isEqualTo(AgentPromptSuggestionProvenance.AI_GENERATED)
    assertThat(updates.last().candidates.single().id).isEqualTo("context.generated.summary.1")
  }

  @Test
  fun defaultGeneratorFallsBackToTemplatesWhenNoAiBackendIsAvailable() {
    val updates = AgentPromptSuggestionAiBackends.withBackendForTest(null) {
      runBlocking(Dispatchers.Default) {
        checkNotNull(AgentPromptSuggestionGenerators.find())
          .generateSuggestions(failedTestRequest())
          .toList()
      }
    }

    assertThat(updates).hasSize(1)
    assertThat(updates.single().candidates.map(AgentPromptSuggestionCandidate::id))
      .containsExactly("tests.fix", "tests.explain", "tests.stabilize")
    assertThat(updates.single().candidates.map(AgentPromptSuggestionCandidate::provenance))
      .containsOnly(AgentPromptSuggestionProvenance.TEMPLATE)
  }

  private fun failedTestRequest(): AgentPromptSuggestionRequest {
    return AgentPromptSuggestionRequest(
      project = ProjectManager.getInstance().defaultProject,
      projectPath = "/work/project/",
      targetModeId = "NEW_TASK",
      contextItems = listOf(
        AgentPromptContextItem(
          rendererId = AgentPromptContextRendererIds.TEST_FAILURES,
          title = "Failing tests",
          body = "failed: ParserTest",
          payload = AgentPromptPayload.obj(
            "statusCounts" to AgentPromptPayload.obj(
              "failed" to AgentPromptPayload.num(2),
            )
          ),
          source = "testRunner",
        )
      ),
    )
  }

  private fun customContextRequest(): AgentPromptSuggestionRequest {
    return AgentPromptSuggestionRequest(
      project = ProjectManager.getInstance().defaultProject,
      projectPath = "/work/project/",
      targetModeId = "NEW_TASK",
      contextItems = listOf(
        AgentPromptContextItem(
          rendererId = "customContext",
          title = "Custom context",
          body = "A proprietary context block that the fallback heuristics do not understand.",
        )
      ),
    )
  }
}
