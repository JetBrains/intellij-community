// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.vcs.render

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptChipRenderInput
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptEnvelopeRenderInput
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayload
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayloadValue
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentPromptVcsCommitsContextRendererBridgeTest {
  private val renderer = AgentPromptVcsCommitsContextRendererBridge()

  @Test
  fun renderEnvelopeUsesPayloadHashes() {
    val item = contextItem(
      body = "",
      payload = AgentPromptPayload.obj(
        "entries" to AgentPromptPayload.arr(
          AgentPromptPayload.obj("hash" to AgentPromptPayload.str("abc12345")),
          AgentPromptPayload.obj("hash" to AgentPromptPayload.str("def67890")),
        )
      )
    )

    val rendered = renderer.renderEnvelope(AgentPromptEnvelopeRenderInput(item = item, projectPath = null))

    assertThat(rendered).isEqualTo("commits:\nabc12345\ndef67890")
  }

  @Test
  fun renderEnvelopeFallsBackToBodyWhenPayloadIsMissing() {
    val item = contextItem(
      body = "abc12345\n\ndef67890\n",
      payload = AgentPromptPayloadValue.Obj.EMPTY,
    )

    val rendered = renderer.renderEnvelope(AgentPromptEnvelopeRenderInput(item = item, projectPath = null))

    assertThat(rendered).isEqualTo("commits:\nabc12345\ndef67890")
  }

  @Test
  fun renderChipUsesFirstCommitFromPayload() {
    val item = contextItem(
      body = "",
      payload = AgentPromptPayload.obj(
        "entries" to AgentPromptPayload.arr(
          AgentPromptPayload.obj("hash" to AgentPromptPayload.str("abc12345")),
          AgentPromptPayload.obj("hash" to AgentPromptPayload.str("def67890")),
        )
      )
    )

    val chip = renderer.renderChip(AgentPromptChipRenderInput(item = item, projectBasePath = null))

    assertThat(chip.text).isEqualTo("Commits: abc12345")
  }

  private fun contextItem(
    body: String,
    payload: AgentPromptPayloadValue,
  ): AgentPromptContextItem {
    return AgentPromptContextItem(
      rendererId = AgentPromptContextRendererIds.VCS_COMMITS,
      title = "Commits",
      body = body,
      payload = payload,
      source = "test",
    )
  }
}
