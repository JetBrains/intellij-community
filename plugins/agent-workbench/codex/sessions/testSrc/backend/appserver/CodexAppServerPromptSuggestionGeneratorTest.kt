// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend.appserver

import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionResult
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncation
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayload
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptSuggestionCandidate
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptSuggestionProvenance
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptSuggestionRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptSuggestionSeeds
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionCandidate as CodexCandidate
import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionRequest as CodexRequest

@TestApplication
class CodexAppServerPromptSuggestionBackendTest {
  @Test
  fun buildsPolishedSuggestionUpdateAndPreservesFallbackSlotIds(): Unit = runBlocking(Dispatchers.Default) {
    var receivedRequest: CodexRequest? = null
    val backend = CodexAppServerPromptSuggestionBackend(
      suggestWithCodexAppServer = { request ->
        receivedRequest = request
        CodexPromptSuggestionResult.PolishedSeeds(
          listOf(
            CodexCandidate(
              id = "tests.fix",
              label = "AI: Fix the ParserTest failure",
              promptText = "Investigate ParserTest, identify the root cause, and implement the minimal fix.",
            ),
            CodexCandidate(
              id = "tests.explain",
              label = "AI: Explain the ParserTest failure",
              promptText = "Explain why ParserTest is failing and point out the relevant code path.",
            ),
            CodexCandidate(
              id = "tests.stabilize",
              label = "AI: Stabilize the ParserTest coverage",
              promptText = "Stabilize the ParserTest scenario and call out any missing assertions or cleanup.",
            ),
          )
        )
      },
      isSuggestionGenerationDisabled = { false },
      modelProvider = { "local-codex" },
      reasoningEffortProvider = { "low" },
    )

    val request = failedTestRequest()
    val fallbackCandidates = AgentPromptSuggestionSeeds.buildDefaultSuggestions(request.contextItems)
    val update = backend.generateSuggestionUpdate(request, fallbackCandidates)

    assertThat(update).isNotNull
    assertThat(update!!.candidates.map(AgentPromptSuggestionCandidate::id))
      .containsExactly("tests.fix", "tests.explain", "tests.stabilize")
    assertThat(update.candidates.map(AgentPromptSuggestionCandidate::provenance))
      .containsOnly(AgentPromptSuggestionProvenance.AI_POLISHED)
    assertThat(update.candidates.map(AgentPromptSuggestionCandidate::label))
      .containsExactly(
        "AI: Fix the ParserTest failure",
        "AI: Explain the ParserTest failure",
        "AI: Stabilize the ParserTest coverage",
      )

    val sentRequest = receivedRequest
    assertThat(sentRequest).isNotNull
    assertThat(sentRequest!!.cwd).isEqualTo("/work/project")
    assertThat(sentRequest.targetMode).isEqualTo("new_task")
    assertThat(sentRequest.model).isEqualTo("local-codex")
    assertThat(sentRequest.reasoningEffort).isEqualTo("low")
    assertThat(sentRequest.maxCandidates).isEqualTo(3)
    assertThat(sentRequest.contextItems.single().rendererId).isEqualTo(AgentPromptContextRendererIds.TEST_FAILURES)
    assertThat(sentRequest.contextItems.single().source).isEqualTo("testRunner")
    assertThat(sentRequest.contextItems.single().itemId).isEqualTo("failure-1")
    assertThat(sentRequest.contextItems.single().parentItemId).isEqualTo("suite-1")
    assertThat(sentRequest.contextItems.single().truncation.reason).isEqualTo("source_limit")
    assertThat(sentRequest.seedCandidates.map(CodexCandidate::id))
      .containsExactly("tests.fix", "tests.explain", "tests.stabilize")
  }

  @Test
  fun disabledSuggestionGenerationSkipsAppServer(): Unit = runBlocking(Dispatchers.Default) {
    var suggestionCallCount = 0
    val backend = CodexAppServerPromptSuggestionBackend(
      suggestWithCodexAppServer = {
        suggestionCallCount += 1
        CodexPromptSuggestionResult.GeneratedCandidates(emptyList())
      },
      isSuggestionGenerationDisabled = { true },
    )

    val request = failedTestRequest()
    val fallbackCandidates = AgentPromptSuggestionSeeds.buildDefaultSuggestions(request.contextItems)

    assertThat(backend.generateSuggestionUpdate(request, fallbackCandidates)).isNull()
    assertThat(suggestionCallCount).isZero()
  }

  @Test
  fun usesAiSuggestionsEvenWhenFallbackSeedsAreEmpty(): Unit = runBlocking(Dispatchers.Default) {
    val backend = CodexAppServerPromptSuggestionBackend(
      suggestWithCodexAppServer = {
        CodexPromptSuggestionResult.GeneratedCandidates(
          listOf(
            CodexCandidate(
              label = "Summarize the custom context",
              promptText = "Summarize the custom context and highlight what needs attention.",
            )
          )
        )
      },
      isSuggestionGenerationDisabled = { false },
      modelProvider = { "local-codex" },
      reasoningEffortProvider = { "low" },
    )

    val request = customContextRequest()
    val update = backend.generateSuggestionUpdate(request, fallbackCandidates = emptyList())

    assertThat(update).isNotNull
    assertThat(update!!.candidates.single().provenance).isEqualTo(AgentPromptSuggestionProvenance.AI_GENERATED)
    assertThat(update.candidates.single().id).startsWith("context.generated.")
  }

  @Test
  fun returnsNullWhenPolishedCandidatesDoNotMatchFallbackSlots(): Unit = runBlocking(Dispatchers.Default) {
    val backend = CodexAppServerPromptSuggestionBackend(
      suggestWithCodexAppServer = {
        CodexPromptSuggestionResult.PolishedSeeds(
          listOf(
            CodexCandidate(
              id = "tests.fix",
              label = "AI: Fix the ParserTest failure",
              promptText = "Investigate ParserTest, identify the root cause, and implement the minimal fix.",
            ),
            CodexCandidate(
              id = "tests.other",
              label = "AI: Explain the ParserTest failure",
              promptText = "Explain why ParserTest is failing and point out the relevant code path.",
            ),
            CodexCandidate(
              id = "tests.stabilize",
              label = "AI: Stabilize the ParserTest coverage",
              promptText = "Stabilize the ParserTest scenario and call out any missing assertions or cleanup.",
            ),
          )
        )
      },
      isSuggestionGenerationDisabled = { false },
      modelProvider = { "local-codex" },
      reasoningEffortProvider = { "low" },
    )

    val request = failedTestRequest()
    val fallbackCandidates = AgentPromptSuggestionSeeds.buildDefaultSuggestions(request.contextItems)

    assertThat(backend.generateSuggestionUpdate(request, fallbackCandidates)).isNull()
  }

  @Test
  fun filtersInvalidAndDuplicateGeneratedCandidatesBeforeReplacingFallback(): Unit = runBlocking(Dispatchers.Default) {
    val backend = CodexAppServerPromptSuggestionBackend(
      suggestWithCodexAppServer = {
        CodexPromptSuggestionResult.GeneratedCandidates(
          listOf(
            CodexCandidate(label = "   ", promptText = "ignored"),
            CodexCandidate(label = "Explain the failure", promptText = "Explain the failing test and likely root cause."),
            CodexCandidate(label = "Explain the failure", promptText = "Explain the failing test and likely root cause."),
            CodexCandidate(label = "Draft a fix plan", promptText = "Draft a concrete plan to fix the failing test."),
          )
        )
      },
      isSuggestionGenerationDisabled = { false },
      modelProvider = { "local-codex" },
      reasoningEffortProvider = { "low" },
    )

    val request = failedTestRequest()
    val fallbackCandidates = AgentPromptSuggestionSeeds.buildDefaultSuggestions(request.contextItems)
    val update = backend.generateSuggestionUpdate(request, fallbackCandidates)

    assertThat(update).isNotNull
    assertThat(update!!.candidates.map(AgentPromptSuggestionCandidate::label))
      .containsExactly("Explain the failure", "Draft a fix plan")
    assertThat(update.candidates.map(AgentPromptSuggestionCandidate::provenance))
      .containsOnly(AgentPromptSuggestionProvenance.AI_GENERATED)
  }

  @Test
  fun returnsNullWhenSuggestionGenerationTimesOut(): Unit = runBlocking(Dispatchers.Default) {
    val backend = CodexAppServerPromptSuggestionBackend(
      suggestWithCodexAppServer = {
        delay(200.milliseconds)
        CodexPromptSuggestionResult.GeneratedCandidates(emptyList())
      },
      timeoutMs = 10,
      isSuggestionGenerationDisabled = { false },
      modelProvider = { "local-codex" },
      reasoningEffortProvider = { "low" },
    )

    val request = failedTestRequest()
    val fallbackCandidates = AgentPromptSuggestionSeeds.buildDefaultSuggestions(request.contextItems)

    assertThat(backend.generateSuggestionUpdate(request, fallbackCandidates)).isNull()
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
          itemId = "failure-1",
          parentItemId = "suite-1",
          source = "testRunner",
          truncation = AgentPromptContextTruncation(
            originalChars = 1200,
            includedChars = 480,
            reason = AgentPromptContextTruncationReason.SOURCE_LIMIT,
          ),
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
