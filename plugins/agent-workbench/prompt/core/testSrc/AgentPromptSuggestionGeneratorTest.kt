// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptSuggestionGeneratorTest {
  @Test
  fun defaultGeneratorEmitsFallbackSuggestionsBeforePolishedAiUpdate() {
    val updates = AgentPromptSuggestionAiBackends.withBackendForTest(
      AgentPromptSuggestionAiBackend { request ->
        AgentPromptSuggestionAiResult.PolishedSeeds(
          request.seedCandidates.map { candidate ->
            AgentPromptSuggestionAiCandidate(
              id = candidate.id,
              label = "AI ${candidate.label}",
              promptText = "${candidate.promptText} Keep the answer concise.",
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
      .containsExactly("tests.fix", "tests.explain", "tests.bisect")
    assertThat(updates.first().candidates.map(AgentPromptSuggestionCandidate::provenance))
      .containsOnly(AgentPromptSuggestionProvenance.TEMPLATE)
    assertThat(updates.last().candidates.map(AgentPromptSuggestionCandidate::id))
      .containsExactly("tests.fix", "tests.explain", "tests.bisect")
    assertThat(updates.last().candidates.map(AgentPromptSuggestionCandidate::provenance))
      .containsOnly(AgentPromptSuggestionProvenance.AI_POLISHED)
  }

  @Test
  fun defaultGeneratorAllowsAiGeneratedSuggestionsWithoutFallbackSeeds() {
    val updates = AgentPromptSuggestionAiBackends.withBackendForTest(
      AgentPromptSuggestionAiBackend {
        AgentPromptSuggestionAiResult.GeneratedCandidates(
          listOf(
            AgentPromptSuggestionAiCandidate(
              label = "Summarize the custom context",
              promptText = "Summarize the custom context and highlight what needs attention.",
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
    assertThat(updates.last().candidates.single().id).isEqualTo("context.generated.summarize-the-custom-context.1")
  }

  @Test
  fun defaultGeneratorKeepsFallbackWhenPolishedCandidatesDoNotMatchFallbackSlots() {
    val updates = AgentPromptSuggestionAiBackends.withBackendForTest(
      AgentPromptSuggestionAiBackend {
        AgentPromptSuggestionAiResult.PolishedSeeds(
          listOf(
            AgentPromptSuggestionAiCandidate(
              id = "tests.fix",
              label = "AI: Fix the ParserTest failure",
              promptText = "Investigate ParserTest, identify the root cause, and implement the minimal fix.",
            ),
            AgentPromptSuggestionAiCandidate(
              id = "tests.other",
              label = "AI: Explain the ParserTest failure",
              promptText = "Explain why ParserTest is failing and point out the relevant code path.",
            ),
            AgentPromptSuggestionAiCandidate(
              id = "tests.bisect",
              label = "AI: Bisect the ParserTest regression",
              promptText = "Identify the commit that broke ParserTest by reading recent diffs of the test and its production paths.",
            ),
          )
        )
      }
    ) {
      runBlocking(Dispatchers.Default) {
        checkNotNull(AgentPromptSuggestionGenerators.find())
          .generateSuggestions(failedTestRequest())
          .toList()
      }
    }

    assertThat(updates).hasSize(1)
    assertThat(updates.single().candidates.map(AgentPromptSuggestionCandidate::provenance))
      .containsOnly(AgentPromptSuggestionProvenance.TEMPLATE)
  }

  @Test
  fun defaultGeneratorFiltersInvalidAndDuplicateGeneratedCandidatesBeforeReplacingFallback() {
    val updates = AgentPromptSuggestionAiBackends.withBackendForTest(
      AgentPromptSuggestionAiBackend {
        AgentPromptSuggestionAiResult.GeneratedCandidates(
          listOf(
            AgentPromptSuggestionAiCandidate(label = "   ", promptText = "ignored"),
            AgentPromptSuggestionAiCandidate(label = "Explain the failure", promptText = "Explain the failing test and likely root cause."),
            AgentPromptSuggestionAiCandidate(label = "Explain the failure", promptText = "Explain the failing test and likely root cause."),
            AgentPromptSuggestionAiCandidate(label = "Draft a fix plan", promptText = "Draft a concrete plan to fix the failing test."),
          )
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
    assertThat(updates.last().candidates.map(AgentPromptSuggestionCandidate::label))
      .containsExactly("Explain the failure", "Draft a fix plan")
    assertThat(updates.last().candidates.map(AgentPromptSuggestionCandidate::provenance))
      .containsOnly(AgentPromptSuggestionProvenance.AI_GENERATED)
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
      .containsExactly("tests.fix", "tests.explain", "tests.bisect")
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
