// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptContextEnvelopeFormatterTest {
  @Test
  fun contextEnvelopePreparationKeepsEmptyContextBelowSoftCap() {
    val selection = AgentPromptContextEnvelopeFormatter.prepareContextEnvelopeSelection(
      items = emptyList(),
      softCapChars = 1,
    )

    assertThat(selection.items).isEmpty()
    assertThat(selection.serializedChars).isEqualTo(0)
    assertThat(selection.exceedsSoftCap).isFalse()
  }

  @Test
  fun contextEnvelopePreparationNormalizesItemsAndReportsSoftCapState() {
    val selection = AgentPromptContextEnvelopeFormatter.prepareContextEnvelopeSelection(
      items = listOf(contextItem(rendererId = AgentPromptContextRendererIds.SNIPPET, title = "Selection", body = "  selected code  ")),
      softCapChars = 1_000,
      projectPath = "/work/repo",
    )

    assertThat(selection.items.single().body).isEqualTo("selected code")
    assertThat(selection.summary.softCapChars).isEqualTo(1_000)
    assertThat(selection.summary.softCapExceeded).isFalse()
    assertThat(selection.summary.autoTrimApplied).isFalse()
    assertThat(selection.exceedsSoftCap).isFalse()
  }

  @Test
  fun contextEnvelopePreparationCanMarkFullOversizedSelectionWithoutTrimming() {
    val oversizedBody = "x".repeat(1_000)
    val selection = AgentPromptContextEnvelopeFormatter.prepareContextEnvelopeSelection(
      items = listOf(contextItem(rendererId = AgentPromptContextRendererIds.SNIPPET, title = "Selection", body = oversizedBody)),
      softCapChars = 400,
      projectPath = "/work/repo",
    )

    val marked = AgentPromptContextEnvelopeFormatter.markSoftCapExceeded(selection)

    assertThat(marked.items.single().body).isEqualTo(oversizedBody)
    assertThat(marked.summary.softCapExceeded).isTrue()
    assertThat(marked.summary.autoTrimApplied).isFalse()
    assertThat(marked.exceedsSoftCap).isTrue()
  }

  @Test
  fun contextEnvelopePreparationAutoTrimsOversizedSelection() {
    val oversizedBody = "x".repeat(AgentPromptContextEnvelopeFormatter.DEFAULT_SOFT_CAP_CHARS * 2)
    val selection = AgentPromptContextEnvelopeFormatter.prepareContextEnvelopeSelection(
      items = listOf(contextItem(rendererId = AgentPromptContextRendererIds.SNIPPET, title = "Selection", body = oversizedBody)),
      projectPath = "/work/repo",
    )

    val trimmed = AgentPromptContextEnvelopeFormatter.autoTrimContextEnvelopeSelection(
      selection = selection,
      projectPath = "/work/repo",
    )

    assertThat(trimmed.summary.softCapExceeded).isTrue()
    assertThat(trimmed.summary.autoTrimApplied).isTrue()
    assertThat(trimmed.items).isNotEmpty()
    assertThat(trimmed.items.joinToString("\n") { item -> item.body }).isNotEqualTo(oversizedBody)
    assertThat(trimmed.serializedChars).isLessThanOrEqualTo(AgentPromptContextEnvelopeFormatter.DEFAULT_SOFT_CAP_CHARS)
    assertThat(trimmed.exceedsSoftCap).isFalse()
  }

  @Test
  fun softCapTrimmingStartsFromLastContextItem() {
    val first = contextItem(rendererId = AgentPromptContextRendererIds.SNIPPET, title = "First", body = "a".repeat(420))
    val second = contextItem(rendererId = AgentPromptContextRendererIds.PATHS, title = "Second", body = "b".repeat(420))

    val result = AgentPromptContextEnvelopeFormatter.applySoftCap(
      items = listOf(first, second),
      softCapChars = 700,
    )

    assertThat(result.items).hasSize(2)
    assertThat(result.items.first().body).isEqualTo(first.body)
    assertThat(result.items.last().truncation.reason).isIn(
      AgentPromptContextTruncationReason.SOFT_CAP_PARTIAL,
      AgentPromptContextTruncationReason.SOFT_CAP_OMITTED,
    )
    assertThat(result.serializedChars).isLessThanOrEqualTo(700)
    assertThat(result.exceedsSoftCap).isFalse()
  }

  @Test
  fun verySmallSoftCapKeepsItemStubsInsteadOfDroppingItems() {
    val result = AgentPromptContextEnvelopeFormatter.applySoftCap(
      items = listOf(
        contextItem(rendererId = AgentPromptContextRendererIds.SNIPPET, title = "A", body = "x".repeat(200)),
        contextItem(rendererId = AgentPromptContextRendererIds.PATHS, title = "B", body = "y".repeat(200)),
      ),
      softCapChars = 200,
    )

    assertThat(result.items).hasSize(2)
    assertThat(result.items.count { item -> item.body == "[omitted due to soft cap]" }).isGreaterThan(0)
    assertThat(result.items)
      .allSatisfy { item ->
        assertThat(item.truncation.reason).isNotEqualTo(AgentPromptContextTruncationReason.NONE)
      }
  }

  private fun contextItem(
    rendererId: String,
    title: String,
    body: String,
  ): AgentPromptContextItem {
    return AgentPromptContextItem(
      rendererId = rendererId,
      title = title,
      body = body,
      source = "test",
    )
  }
}
