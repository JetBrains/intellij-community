// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.testrunner.render

import com.intellij.agent.workbench.prompt.core.AgentPromptChipRenderInput
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptEnvelopeRenderInput
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentPromptTestFailuresContextRendererBridgeTest {
  private val renderer = AgentPromptTestFailuresContextRendererBridge()

  @Test
  fun renderEnvelopeUsesPayloadEntries() {
    val item = contextItem(
      body = "",
      payload = AgentPromptPayload.obj(
        "entries" to AgentPromptPayload.arr(
          AgentPromptPayload.obj(
            "name" to AgentPromptPayload.str("testA"),
            "locationUrl" to AgentPromptPayload.str("java:test://com.example.Suite/testA"),
            "status" to AgentPromptPayload.str("failed"),
            "assertionMessage" to AgentPromptPayload.str("expected 1"),
          ),
          AgentPromptPayload.obj(
            "name" to AgentPromptPayload.str("testB"),
            "status" to AgentPromptPayload.str("passed"),
          ),
        )
      )
    )

    val rendered = renderer.renderEnvelope(AgentPromptEnvelopeRenderInput(item = item, projectPath = null))

    assertThat(rendered).isEqualTo(
      "selected tests (failed:1 passed:1)\n" +
      "failed: com.example.Suite#testA | assertion: expected 1\n" +
      "passed: testB"
    )
  }

  @Test
  fun renderEnvelopeConvertsLegacyDotJavaLocation() {
    val item = contextItem(
      body = "",
      payload = AgentPromptPayload.obj(
        "entries" to AgentPromptPayload.arr(
          AgentPromptPayload.obj(
            "name" to AgentPromptPayload.str("testA"),
            "locationUrl" to AgentPromptPayload.str("java:test://Suite.testA"),
            "status" to AgentPromptPayload.str("failed"),
          ),
        )
      )
    )

    val rendered = renderer.renderEnvelope(AgentPromptEnvelopeRenderInput(item = item, projectPath = null))

    assertThat(rendered).isEqualTo(
      "failed tests\n" +
      "Suite#testA"
    )
  }

  @Test
  fun renderEnvelopeAppendsFocusedOutputBlockWhenPresent() {
    val item = contextItem(
      body = "",
      payload = AgentPromptPayload.obj(
        "entries" to AgentPromptPayload.arr(
          AgentPromptPayload.obj(
            "name" to AgentPromptPayload.str("testA"),
            "locationUrl" to AgentPromptPayload.str("java:test://Suite.testA"),
            "status" to AgentPromptPayload.str("failed"),
          ),
        ),
        "consoleOutput" to AgentPromptPayload.str("AssertionError: boom\nat Suite.testA(Suite.kt:42)"),
      )
    )

    val rendered = renderer.renderEnvelope(AgentPromptEnvelopeRenderInput(item = item, projectPath = null))

    assertThat(rendered).isEqualTo(
      "failed tests\n" +
      "Suite#testA\n\n" +
      "failure console output:\n" +
      "```text\n" +
      "AssertionError: boom\n" +
      "at Suite.testA(Suite.kt:42)\n" +
      "```"
    )
  }

  @Test
  fun renderEnvelopeFallsBackToBodyWhenPayloadIsMissing() {
    val item = contextItem(
      body = "failed: java:test://Suite.testA\n\npassed: java:test://Suite.testB\n",
      payload = AgentPromptPayloadValue.Obj.EMPTY,
    )

    val rendered = renderer.renderEnvelope(AgentPromptEnvelopeRenderInput(item = item, projectPath = null))

    assertThat(rendered).isEqualTo(
      "selected tests (failed:1 passed:1)\n" +
      "failed: Suite#testA\n" +
      "passed: Suite#testB"
    )
  }

  @Test
  fun renderChipUsesFirstPayloadEntry() {
    val item = contextItem(
      body = "",
      payload = AgentPromptPayload.obj(
        "entries" to AgentPromptPayload.arr(
          AgentPromptPayload.obj(
            "name" to AgentPromptPayload.str("testA"),
            "locationUrl" to AgentPromptPayload.str("java:test://com.example.Suite/testA"),
            "status" to AgentPromptPayload.str("failed"),
          ),
          AgentPromptPayload.obj(
            "name" to AgentPromptPayload.str("testB"),
            "status" to AgentPromptPayload.str("failed"),
          ),
        )
      )
    )

    val chip = renderer.renderChip(AgentPromptChipRenderInput(item = item, projectBasePath = null))

    assertThat(chip.text).isEqualTo("failed tests: com.example.Suite#testA")
  }

  @Test
  fun renderChipIgnoresFocusedOutput() {
    val item = contextItem(
      body = "",
      payload = AgentPromptPayload.obj(
        "entries" to AgentPromptPayload.arr(
          AgentPromptPayload.obj(
            "name" to AgentPromptPayload.str("testA"),
            "locationUrl" to AgentPromptPayload.str("java:test://Suite.testA"),
            "status" to AgentPromptPayload.str("failed"),
          ),
        ),
        "consoleOutput" to AgentPromptPayload.str("AssertionError: boom"),
      )
    )

    val chip = renderer.renderChip(AgentPromptChipRenderInput(item = item, projectBasePath = null))

    assertThat(chip.text).isEqualTo("failed tests: Suite#testA")
  }

  private fun contextItem(
    body: String,
    payload: AgentPromptPayloadValue,
  ): AgentPromptContextItem {
    return AgentPromptContextItem(
      rendererId = AgentPromptContextRendererIds.TEST_FAILURES,
      title = "Tests",
      body = body,
      payload = payload,
      source = "test",
    )
  }
}
