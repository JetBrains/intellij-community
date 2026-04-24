// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend.appserver

import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionContextItem
import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionRequest
import com.intellij.agent.workbench.codex.common.CodexPromptSuggestionResult
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
class CodexPromptSuggestionAppServerServiceTest {
  @Test
  fun canceledQueuedRequestDoesNotStartAfterMutexIsReleased(): Unit = runBlocking(Dispatchers.Default) {
    val firstStarted = CompletableDeferred<Unit>()
    val releaseFirst = CompletableDeferred<Unit>()
    val observedTitles = mutableListOf<String>()
    val expectedResult = CodexPromptSuggestionResult.GeneratedCandidates(emptyList())
    val service = CodexPromptSuggestionAppServerService(
      serviceScope = this,
      suggestWithClient = { request ->
        val title = request.contextItems.single().title ?: "missing"
        synchronized(observedTitles) {
          observedTitles += title
        }
        if (title == "first") {
          firstStarted.complete(Unit)
          releaseFirst.await()
          null
        }
        else {
          expectedResult
        }
      },
    )

    val first = async(start = CoroutineStart.UNDISPATCHED) {
      service.suggestPrompt(request(title = "first"))
    }
    withTimeout(5.seconds) {
      firstStarted.await()
    }

    val second = async(start = CoroutineStart.UNDISPATCHED) {
      service.suggestPrompt(request(title = "second"))
    }
    second.cancel()

    val third = async(start = CoroutineStart.UNDISPATCHED) {
      service.suggestPrompt(request(title = "third"))
    }

    releaseFirst.complete(Unit)

    assertThat(first.await()).isNull()
    try {
      second.await()
      fail("Expected CancellationException")
    }
    catch (_: CancellationException) {
    }
    assertThat(third.await()).isEqualTo(expectedResult)
    assertThat(observedTitles).containsExactly("first", "third")
  }

  private fun request(title: String): CodexPromptSuggestionRequest {
    return CodexPromptSuggestionRequest(
      cwd = "/work/project",
      targetMode = "new_task",
      model = "mock-model",
      reasoningEffort = "low",
      contextItems = listOf(
        CodexPromptSuggestionContextItem(
          rendererId = "testFailures",
          title = title,
          body = "failed: ParserTest",
        )
      ),
    )
  }
}
