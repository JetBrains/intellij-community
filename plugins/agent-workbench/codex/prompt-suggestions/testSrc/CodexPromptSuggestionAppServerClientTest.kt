// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.prompt.suggestions

import com.intellij.platform.ai.agent.codex.common.CodexAppServerException
import com.intellij.platform.ai.agent.codex.common.writeObject
import com.intellij.platform.ai.agent.codex.common.writeObjectField
import com.intellij.platform.ai.agent.codex.common.writeStringArrayField
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import tools.jackson.core.JsonGenerator
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexPromptSuggestionAppServerClientTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun readOnlyEphemeralTurnUsesTurnFlowAndReturnsAssistantMessage(): Unit = runBlocking(Dispatchers.Default) {
    val backendDir = tempDir.resolve("backend-prompt-suggest")
    Files.createDirectories(backendDir)
    val requestPayloadLogPath = backendDir.resolve("prompt-suggest-requests.log")
    val client = createMockPromptSuggestionClient(
      scope = this,
      tempDir = backendDir,
      environmentOverrides = mapOf(
        "CODEX_TEST_REQUEST_PAYLOAD_LOG" to requestPayloadLogPath.toString(),
      ),
    )
    try {
      val payload = client.runTestReadOnlyEphemeralTurn()

      assertGeneratedPromptSuggestionPayload(payload)

      val payloadLog = Files.readString(requestPayloadLogPath)
      assertThat(payloadLog).contains("\"method\":\"thread/start\"")
      assertThat(payloadLog).contains("\"cwd\":\"/work/project\"")
      assertThat(payloadLog).contains("\"approvalPolicy\":\"never\"")
      assertThat(payloadLog).contains("\"sandbox\":\"read-only\"")
      assertThat(payloadLog).contains("\"ephemeral\":true")
      assertThat(payloadLog).contains("\"experimentalRawEvents\":false")
      assertThat(payloadLog).contains("\"persistExtendedHistory\":false")
      assertThat(payloadLog).contains("\"method\":\"turn/start\"")
      assertThat(payloadLog).contains("\"model\":\"gpt-5.4\"")
      assertThat(payloadLog).contains("\"effort\":\"low\"")
      assertThat(payloadLog).contains("\"outputSchema\":{\"type\":\"object\"")
      assertThat(payloadLog).contains("Run a read-only ephemeral test turn.")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun readOnlyEphemeralTurnUsesRealCodexAppServerWithStrictOutputSchema(): Unit = runBlocking(Dispatchers.Default) {
    val backendDir = tempDir.resolve("backend-prompt-suggest-real")
    Files.createDirectories(backendDir)
    createRealPromptSuggestionHarness(
      scope = this,
      tempDir = backendDir,
      responsePlans = listOf(MockResponsesPlan.completedAssistantMessage(renderGeneratedPromptSuggestionPayload())),
    ).use { harness ->
      val payload = harness.client.runTestReadOnlyEphemeralTurn(
        cwd = harness.projectDir.toString(),
        model = "mock-model",
      )

      assertGeneratedPromptSuggestionPayload(payload)
      val requestBody = harness.responsesServer.requests().single()
      assertThat(requestBody).contains("\"strict\":true")
      assertThat(requestBody).contains("\"type\":\"object\"")
    }
  }

  @Test
  fun readOnlyEphemeralTurnReturnsAssistantMessageText(): Unit = runBlocking(Dispatchers.Default) {
    val backendDir = tempDir.resolve("backend-prompt-suggest-polished")
    Files.createDirectories(backendDir)
    val client = createMockPromptSuggestionClient(
      scope = this,
      tempDir = backendDir,
      environmentOverrides = mapOf(
        "CODEX_TEST_PROMPT_SUGGEST_KIND" to "polished",
      ),
    )
    try {
      val payload = client.runTestReadOnlyEphemeralTurn()

      assertThat(payload).contains("\"kind\":\"polishedSeeds\"")
      assertThat(payload).contains("AI: Fix the ParserTest failure")
      assertThat(payload).contains("AI: Explain the ParserTest failure")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun readOnlyEphemeralTurnSendsInterruptWhenCancelledBeforeTerminalCompletion(): Unit = runBlocking(Dispatchers.Default) {
    val backendDir = tempDir.resolve("backend-prompt-suggest-timeout")
    Files.createDirectories(backendDir)
    val requestLogPath = backendDir.resolve("prompt-suggest-timeout-requests.log")
    val client = createMockPromptSuggestionClient(
      scope = this,
      tempDir = backendDir,
      environmentOverrides = mapOf(
        "CODEX_TEST_REQUEST_LOG" to requestLogPath.toString(),
        "CODEX_TEST_PROMPT_SUGGEST_LIFECYCLE" to "wait_for_interrupt",
      ),
    )
    try {
      val suggestion = async(start = CoroutineStart.UNDISPATCHED) {
        client.runTestReadOnlyEphemeralTurn()
      }

      waitForRequestLogMethod(requestLogPath, method = "turn/start")

      suggestion.cancel()
      try {
        suggestion.await()
        fail("Expected CancellationException")
      }
      catch (_: CancellationException) {
      }

      waitForRequestLogMethod(requestLogPath, method = "turn/interrupt")
      assertThat(Files.readAllLines(requestLogPath)).contains("turn/interrupt")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun readOnlyEphemeralTurnKeepsClientWhenInterruptResponseIsMissingButTerminalCompletionArrives(): Unit =
    runBlocking(Dispatchers.Default) {
      val backendDir = tempDir.resolve("backend-prompt-suggest-missing-interrupt-response")
      Files.createDirectories(backendDir)
      val requestLogPath = backendDir.resolve("prompt-suggest-missing-interrupt-response-requests.log")
      val client = createMockPromptSuggestionClient(
        scope = this,
        tempDir = backendDir,
        environmentOverrides = mapOf(
          "CODEX_TEST_REQUEST_LOG" to requestLogPath.toString(),
          "CODEX_TEST_PROMPT_SUGGEST_LIFECYCLE" to "wait_for_interrupt_without_response,completed",
        ),
      )
      try {
        val suggestion = async(start = CoroutineStart.UNDISPATCHED) {
          client.runTestReadOnlyEphemeralTurn()
        }

        waitForRequestLogMethod(requestLogPath, method = "turn/start")

        suggestion.cancel()
        try {
          suggestion.await()
          fail("Expected CancellationException")
        }
        catch (_: CancellationException) {
        }

        assertGeneratedPromptSuggestionPayload(client.runTestReadOnlyEphemeralTurn())

        val methods = Files.readAllLines(requestLogPath)
        assertThat(methods).contains("turn/interrupt")
        assertThat(methods.count { it == "initialize" }).isEqualTo(1)
      }
      finally {
        client.shutdown()
      }
    }

  @Test
  fun readOnlyEphemeralTurnResetsClientWhenInterruptDoesNotReachTerminalCompletion(): Unit = runBlocking(Dispatchers.Default) {
    val backendDir = tempDir.resolve("backend-prompt-suggest-missing-terminal")
    Files.createDirectories(backendDir)
    val requestLogPath = backendDir.resolve("prompt-suggest-missing-terminal-requests.log")
    val lifecycleStatePath = backendDir.resolve("prompt-suggest-missing-terminal.lifecycle")
    val client = createMockPromptSuggestionClient(
      scope = this,
      tempDir = backendDir,
      environmentOverrides = mapOf(
        "CODEX_TEST_REQUEST_LOG" to requestLogPath.toString(),
        "CODEX_TEST_PROMPT_SUGGEST_LIFECYCLE" to "wait_for_interrupt_without_terminal,completed",
        "CODEX_TEST_PROMPT_SUGGEST_LIFECYCLE_STATE_FILE" to lifecycleStatePath.toString(),
      ),
    )
    try {
      val suggestion = async(start = CoroutineStart.UNDISPATCHED) {
        client.runTestReadOnlyEphemeralTurn()
      }

      waitForRequestLogMethod(requestLogPath, method = "turn/start")

      suggestion.cancel()
      try {
        suggestion.await()
        fail("Expected CancellationException")
      }
      catch (_: CancellationException) {
      }

      assertGeneratedPromptSuggestionPayload(client.runTestReadOnlyEphemeralTurn())

      val methods = Files.readAllLines(requestLogPath)
      assertThat(methods).contains("turn/interrupt")
      assertThat(methods.count { it == "initialize" }).isEqualTo(2)
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun readOnlyEphemeralTurnReturnsNullWhenTurnCompletesAsInterrupted(): Unit = runBlocking(Dispatchers.Default) {
    val backendDir = tempDir.resolve("backend-prompt-suggest-interrupted")
    Files.createDirectories(backendDir)
    val requestLogPath = backendDir.resolve("prompt-suggest-interrupted-requests.log")
    val client = createMockPromptSuggestionClient(
      scope = this,
      tempDir = backendDir,
      environmentOverrides = mapOf(
        "CODEX_TEST_REQUEST_LOG" to requestLogPath.toString(),
        "CODEX_TEST_PROMPT_SUGGEST_LIFECYCLE" to "interrupted",
      ),
    )
    try {
      assertThat(client.runTestReadOnlyEphemeralTurn()).isNull()
      assertThat(Files.readAllLines(requestLogPath)).doesNotContain("turn/interrupt")
    }
    finally {
      client.shutdown()
    }
  }

  @Test
  fun readOnlyEphemeralTurnRealParentTimeoutDoesNotPoisonClient(): Unit = runBlocking(Dispatchers.Default) {
    val backendDir = tempDir.resolve("backend-prompt-suggest-real-timeout")
    Files.createDirectories(backendDir)
    createRealPromptSuggestionHarness(
      scope = this,
      tempDir = backendDir,
      responsePlans = listOf(
        MockResponsesPlan.inProgressAssistantMessage(renderGeneratedPromptSuggestionPayload()),
        MockResponsesPlan.completedAssistantMessage(renderGeneratedPromptSuggestionPayload()),
      ),
    ).use { harness ->
      assertThat(withTimeoutOrNull(1500.milliseconds) {
        harness.client.runTestReadOnlyEphemeralTurn(
          cwd = harness.projectDir.toString(),
          model = "mock-model",
        )
      }).isNull()

      assertGeneratedPromptSuggestionPayload(
        harness.client.runTestReadOnlyEphemeralTurn(
          cwd = harness.projectDir.toString(),
          model = "mock-model",
        )
      )
      assertThat(harness.responsesServer.requests()).hasSize(2)
    }
  }

  @Test
  fun readOnlyEphemeralTurnSurfacesFailedTurnErrors(): Unit = runBlocking(Dispatchers.Default) {
    val backendDir = tempDir.resolve("backend-prompt-suggest-failed")
    Files.createDirectories(backendDir)
    val client = createMockPromptSuggestionClient(
      scope = this,
      tempDir = backendDir,
      environmentOverrides = mapOf(
        "CODEX_TEST_PROMPT_SUGGEST_LIFECYCLE" to "failed",
        "CODEX_TEST_PROMPT_SUGGEST_ERROR_MESSAGE" to "prompt turn failed",
      ),
    )
    try {
      try {
        client.runTestReadOnlyEphemeralTurn()
        fail("Expected CodexAppServerException")
      }
      catch (e: CodexAppServerException) {
        assertThat(e.message).contains("prompt turn failed")
      }
    }
    finally {
      client.shutdown()
    }
  }

  private suspend fun waitForRequestLogMethod(
    requestLogPath: Path,
    method: String,
    timeout: kotlin.time.Duration = 30.seconds,
  ) {
    withTimeout(timeout) {
      while (true) {
        if (Files.exists(requestLogPath) && Files.readAllLines(requestLogPath).contains(method)) {
          return@withTimeout
        }
        delay(10.milliseconds)
      }
    }
  }

  private suspend fun CodexPromptSuggestionAppServerClient.runTestReadOnlyEphemeralTurn(
    cwd: String = "/work/project",
    model: String = "gpt-5.4",
  ): String? {
    return runReadOnlyEphemeralTurn(
      cwd = cwd,
      inputText = "Run a read-only ephemeral test turn.",
      model = model,
      reasoningEffort = "low",
      outputSchemaWriter = ::writeTestReadOnlyEphemeralTurnOutputSchema,
    )
  }

  private fun writeTestReadOnlyEphemeralTurnOutputSchema(generator: JsonGenerator) {
    generator.writeObject {
      writeStringProperty("type", "object")
      writeBooleanProperty("additionalProperties", false)
      writeStringArrayField("required", "kind", "candidates")
      writeObjectField("properties") {
        writeObjectField("kind") {
          writeStringProperty("type", "string")
          writeStringArrayField("enum", "generatedCandidates")
        }

        writeObjectField("candidates") {
          writeStringProperty("type", "array")
          writeObjectField("items") {
            writeStringProperty("type", "object")
            writeBooleanProperty("additionalProperties", false)
            writeStringArrayField("required", "id", "label", "promptText")
            writeObjectField("properties") {
              writeObjectField("id") {
                writeStringArrayField("type", "string", "null")
              }
              writeObjectField("label") {
                writeStringProperty("type", "string")
              }
              writeObjectField("promptText") {
                writeStringProperty("type", "string")
              }
            }
          }
        }
      }
    }
  }

  private fun assertGeneratedPromptSuggestionPayload(payload: String?) {
    assertThat(payload).contains("\"kind\":\"generatedCandidates\"")
    assertThat(payload).contains("AI: Investigate provided context")
    assertThat(payload).contains("AI: Summarize provided context")
  }

  private fun renderGeneratedPromptSuggestionPayload(): String {
    return """
      {"kind":"generatedCandidates","candidates":[
        {"id":null,"label":"AI: Investigate provided context","promptText":"Investigate the provided context and explain the next steps."},
        {"id":null,"label":"AI: Summarize provided context","promptText":"Summarize the relevant context before making changes."}
      ]}
    """.trimIndent()
  }
}
