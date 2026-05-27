// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.vcs.render

import com.intellij.agent.workbench.prompt.core.AgentPromptChipRenderInput
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptEnvelopeRenderInput
import com.intellij.agent.workbench.prompt.core.AgentPromptPayload
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
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

  @Test
  fun renderChipUsesSubjectAndCommitCountFromPayload() {
    val item = contextItem(
      body = "",
      payload = AgentPromptPayload.obj(
        "entries" to AgentPromptPayload.arr(
          AgentPromptPayload.obj(
            "hash" to AgentPromptPayload.str("abc12345abcdef"),
            "subject" to AgentPromptPayload.str("Fix TEST-101 regression"),
            "author" to AgentPromptPayload.str("Test User"),
            "commitTimeMs" to AgentPromptPayloadValue.Num("1710000000000"),
            "rootName" to AgentPromptPayload.str("repo"),
          ),
          AgentPromptPayload.obj(
            "hash" to AgentPromptPayload.str("def67890abcdef"),
            "subject" to AgentPromptPayload.str("Update docs"),
          ),
        ),
        "selectedCount" to AgentPromptPayload.num(2),
      )
    )

    val chip = renderer.renderChip(AgentPromptChipRenderInput(item = item, projectBasePath = null))

    assertThat(chip.text).isEqualTo("Commits: Fix TEST-101 regression +1")
    assertThat(chip.tooltipText).contains("abc12345  Fix TEST-101 regression")
    assertThat(chip.tooltipText).contains("Test User")
    assertThat(chip.tooltipText).contains("repo")
  }

  @Test
  fun renderChipShortensLongSubjectButKeepsFullTooltip() {
    val subject = "Fix VCS commit chip preview width by trimming long subjects"
    val item = contextItem(
      body = "",
      payload = AgentPromptPayload.obj(
        "entries" to AgentPromptPayload.arr(
          AgentPromptPayload.obj(
            "hash" to AgentPromptPayload.str("abc12345abcdef"),
            "subject" to AgentPromptPayload.str(subject),
          ),
        )
      )
    )

    val chip = renderer.renderChip(AgentPromptChipRenderInput(item = item, projectBasePath = null))

    assertThat(chip.text).isEqualTo("Commits: ${subject.take(40)}\u2026")
    assertThat(chip.tooltipText).contains("abc12345  $subject")
  }

  @Test
  fun renderEnvelopeKeepsHashOnlyOutputWhenPayloadHasMetadata() {
    val item = contextItem(
      body = "",
      payload = AgentPromptPayload.obj(
        "entries" to AgentPromptPayload.arr(
          AgentPromptPayload.obj(
            "hash" to AgentPromptPayload.str("abc12345abcdef"),
            "subject" to AgentPromptPayload.str("Fix TEST-101 regression"),
          ),
        )
      )
    )

    val rendered = renderer.renderEnvelope(AgentPromptEnvelopeRenderInput(item = item, projectPath = null))

    assertThat(rendered).isEqualTo("commits:\nabc12345abcdef")
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
