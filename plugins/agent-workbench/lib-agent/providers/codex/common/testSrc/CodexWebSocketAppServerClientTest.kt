// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.StringWriter

class CodexWebSocketAppServerClientTest {
  private val protocol = CodexAppServerProtocol()

  @Test
  fun threadStartPayloadIncludesYoloParamsAndGenerationSettings() {
    val payload = renderThreadStartPayload(
      CodexThreadStartParams(
        cwd = "/work/project",
        model = "gpt-5.1-codex",
        reasoningEffort = "high",
        approvalPolicy = "on-request",
        sandbox = "workspace-write",
      )
    )

    assertThat(payload).contains("\"method\":\"thread/start\"")
    assertThat(payload).contains("\"cwd\":\"/work/project\"")
    assertThat(payload).contains("\"model\":\"gpt-5.1-codex\"")
    assertThat(payload).contains("\"reasoningEffort\":\"high\"")
    assertThat(payload).contains("\"approvalPolicy\":\"on-request\"")
    assertThat(payload).contains("\"sandbox\":\"workspace-write\"")
  }

  private fun renderThreadStartPayload(params: CodexThreadStartParams): String {
    val writer = StringWriter()
    protocol.writePayload(writer) { generator ->
      generator.writeStartObject()
      generator.writeStringField("id", "1")
      generator.writeStringField("method", "thread/start")
      generator.writeFieldName("params")
      writeThreadStartParams(generator, params)
      generator.writeEndObject()
    }
    return writer.toString()
  }
}
