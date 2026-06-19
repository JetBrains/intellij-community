// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.prompt.suggestions

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncation
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionAiCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionAiRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionAiResult
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptSuggestionSeeds
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexAppServerPromptSuggestionBackendTest {
  @Test
  fun buildsCodexRequestAndReturnsAiResult(): Unit = runBlocking(Dispatchers.Default) {
    var receivedRequest: CodexPromptSuggestionRequest? = null
    val expectedResult = AgentPromptSuggestionAiResult.PolishedSeeds(
      listOf(
        AgentPromptSuggestionAiCandidate(
          id = "tests.fix",
          label = "AI: Fix the ParserTest failure",
          promptText = "Investigate ParserTest, identify the root cause, and implement the minimal fix.",
        ),
        AgentPromptSuggestionAiCandidate(
          id = "tests.explain",
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
    val backend = CodexAppServerPromptSuggestionBackend(
      suggestWithCodexAppServer = { request ->
        receivedRequest = request
        expectedResult
      },
      isSuggestionGenerationDisabled = { false },
      modelProvider = { "local-codex" },
      reasoningEffortProvider = { "low" },
    )

    val request = failedTestAiRequest()
    val result = backend.generateSuggestionResult(request)

    assertThat(result).isEqualTo(expectedResult)

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
    assertThat(sentRequest.seedCandidates.map(AgentPromptSuggestionAiCandidate::id))
      .containsExactly("tests.fix", "tests.explain", "tests.bisect")
  }

  @Test
  fun disabledSuggestionGenerationSkipsAppServer(): Unit = runBlocking(Dispatchers.Default) {
    var suggestionCallCount = 0
    val backend = CodexAppServerPromptSuggestionBackend(
      suggestWithCodexAppServer = {
        suggestionCallCount += 1
        AgentPromptSuggestionAiResult.GeneratedCandidates(emptyList())
      },
      isSuggestionGenerationDisabled = { true },
    )

    assertThat(backend.generateSuggestionResult(failedTestAiRequest())).isNull()
    assertThat(suggestionCallCount).isZero()
  }

  @Test
  fun usesAiSuggestionsEvenWhenFallbackSeedsAreEmpty(): Unit = runBlocking(Dispatchers.Default) {
    val expectedResult = AgentPromptSuggestionAiResult.GeneratedCandidates(
      listOf(
        AgentPromptSuggestionAiCandidate(
          label = "Summarize the custom context",
          promptText = "Summarize the custom context and highlight what needs attention.",
        )
      )
    )
    val backend = CodexAppServerPromptSuggestionBackend(
      suggestWithCodexAppServer = { expectedResult },
      isSuggestionGenerationDisabled = { false },
      modelProvider = { "local-codex" },
      reasoningEffortProvider = { "low" },
    )

    assertThat(backend.generateSuggestionResult(customContextAiRequest())).isEqualTo(expectedResult)
  }

  @Test
  fun returnsNullWhenSuggestionGenerationTimesOut(): Unit = runBlocking(Dispatchers.Default) {
    val backend = CodexAppServerPromptSuggestionBackend(
      suggestWithCodexAppServer = {
        delay(200.milliseconds)
        AgentPromptSuggestionAiResult.GeneratedCandidates(emptyList())
      },
      timeoutMs = 10,
      isSuggestionGenerationDisabled = { false },
      modelProvider = { "local-codex" },
      reasoningEffortProvider = { "low" },
    )

    assertThat(backend.generateSuggestionResult(failedTestAiRequest())).isNull()
  }

  private fun failedTestAiRequest(): AgentPromptSuggestionAiRequest {
    return failedTestRequest().toAiRequest()
  }

  private fun customContextAiRequest(): AgentPromptSuggestionAiRequest {
    return customContextRequest().toAiRequest()
  }

  private fun AgentPromptSuggestionRequest.toAiRequest(): AgentPromptSuggestionAiRequest {
    return AgentPromptSuggestionAiRequest(
      project = project,
      projectPath = projectPath,
      targetModeId = targetModeId,
      contextItems = contextItems,
      seedCandidates = AgentPromptSuggestionSeeds.buildDefaultSuggestions(contextItems),
    )
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
