// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.ide

import tools.jackson.core.JsonParser
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.json.JsonFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.nio.charset.StandardCharsets

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexIdeContextIpcProtocolTest {
  @Test
  fun returnsIdeContextResponse(): Unit = runBlocking(Dispatchers.Default) {
    val protocol = CodexIdeContextIpcProtocol(
      contextCollector = { workspaceRoot ->
        assertThat(workspaceRoot).isEqualTo("/repo")
        context()
      },
    )

    val response = protocol.handlePayload(request("ide-context"))!!.asText()

    assertThat(response).contains("\"type\":\"response\"")
    assertThat(response).contains("\"requestId\":\"request-1\"")
    assertThat(response).contains("\"resultType\":\"success\"")
    assertThat(response).contains("\"path\":\"src/Main.kt\"")
    assertThat(response).contains("\"selection\":{\"start\":{\"line\":1,\"character\":2}")
    assertThat(response).contains("\"activeSelectionContent\":\"selected\"")
    assertThat(response).contains("\"openTabs\":[{\"label\":\"Main.kt\"")
  }

  @Test
  fun returnsNoClientFoundWhenWorkspaceIsNotOpen(): Unit = runBlocking(Dispatchers.Default) {
    val protocol = CodexIdeContextIpcProtocol(contextCollector = { null })

    val response = protocol.handlePayload(request("ide-context"))!!.asText()

    assertThat(response).contains("\"resultType\":\"error\"")
    assertThat(response).contains("\"error\":\"no-client-found\"")
  }

  @Test
  fun returnsNoHandlerForUnsupportedRequest(): Unit = runBlocking(Dispatchers.Default) {
    val protocol = CodexIdeContextIpcProtocol(contextCollector = { context() })

    val response = protocol.handlePayload(request("unknown-method"))!!.asText()

    assertThat(response).contains("\"resultType\":\"error\"")
    assertThat(response).contains("\"error\":\"no-handler-for-request\"")
  }

  @Test
  fun returnsVersionMismatchForUnsupportedVersion(): Unit = runBlocking(Dispatchers.Default) {
    val protocol = CodexIdeContextIpcProtocol(contextCollector = { context() })

    val response = protocol.handlePayload(request("ide-context", version = "1"))!!.asText()

    assertThat(response).contains("\"resultType\":\"error\"")
    assertThat(response).contains("\"error\":\"request-version-mismatch\"")
  }

  @Test
  fun returnsInvalidRequestForMissingVersion(): Unit = runBlocking(Dispatchers.Default) {
    val protocol = CodexIdeContextIpcProtocol(contextCollector = { context() })

    val response = protocol.handlePayload(requestWithoutVersion())!!.asText()

    assertThat(response).contains("\"resultType\":\"error\"")
    assertThat(response).contains("\"error\":\"invalid-request\"")
  }

  @Test
  fun returnsInvalidRequestForMalformedVersion(): Unit = runBlocking(Dispatchers.Default) {
    val protocol = CodexIdeContextIpcProtocol(contextCollector = { context() })

    val response = protocol.handlePayload(request("ide-context", version = "\"bad\""))!!.asText()

    assertThat(response).contains("\"resultType\":\"error\"")
    assertThat(response).contains("\"error\":\"invalid-request\"")
  }

  @Test
  fun rethrowsCancellationDuringRequestParsing(): Unit = runBlocking(Dispatchers.Default) {
    val cancellation = CancellationException("cancelled")
    val protocol = CodexIdeContextIpcProtocol(
      contextCollector = { context() },
      jsonFactory = object : JsonFactory() {
        override fun createParser(readCtxt: ObjectReadContext, data: ByteArray): JsonParser {
          throw cancellation
        }
      },
    )

    val failure = runCatching { protocol.handlePayload(request("ide-context")) }.exceptionOrNull()

    assertThat(failure).isSameAs(cancellation)
  }

  private fun request(method: String, version: String = "0"): ByteArray {
    return """
      {"type":"request","requestId":"request-1","sourceClientId":"codex-tui","version":$version,"method":"$method","params":{"workspaceRoot":"/repo"}}
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)
  }

  private fun requestWithoutVersion(): ByteArray {
    return """
      {"type":"request","requestId":"request-1","sourceClientId":"codex-tui","method":"ide-context","params":{"workspaceRoot":"/repo"}}
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)
  }

  private fun ByteArray.asText(): String = toString(StandardCharsets.UTF_8)

  private fun context(): CodexIdeContext {
    return CodexIdeContext(
      activeFile = CodexIdeActiveFile(
        label = "Main.kt",
        path = "src/Main.kt",
        fsPath = "/repo/src/Main.kt",
        selection = CodexIdeRange(start = CodexIdePosition(1, 2), end = CodexIdePosition(1, 10)),
        activeSelectionContent = "selected",
        selections = listOf(CodexIdeRange(start = CodexIdePosition(1, 2), end = CodexIdePosition(1, 10))),
      ),
      openTabs = listOf(CodexIdeFileDescriptor(label = "Main.kt", path = "src/Main.kt", fsPath = "/repo/src/Main.kt")),
    )
  }
}
