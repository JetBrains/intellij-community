// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncationReason
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentPromptContextSoftCapPolicyTest {
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
