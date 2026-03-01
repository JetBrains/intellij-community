// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextMetadataKeys
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncationReasons
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentPromptContextSoftCapPolicyTest {
  @Test
  fun softCapTrimmingStartsFromLastContextItem() {
    val first = contextItem(kindId = "snippet", title = "First", content = "a".repeat(420))
    val second = contextItem(kindId = "paths", title = "Second", content = "b".repeat(420))

    val result = AgentPromptContextEnvelopeFormatter.applySoftCap(
      items = listOf(first, second),
      softCapChars = 700,
    )

    assertThat(result.items).hasSize(2)
    assertThat(result.items.first().content).isEqualTo(first.content)
    assertThat(result.items.last().metadata[AgentPromptContextMetadataKeys.TRUNCATED]).isEqualTo("true")
    assertThat(result.items.last().metadata[AgentPromptContextMetadataKeys.TRUNCATION_REASON]).isIn(
      AgentPromptContextTruncationReasons.SOFT_CAP_PARTIAL,
      AgentPromptContextTruncationReasons.SOFT_CAP_OMITTED,
    )
    assertThat(result.serializedChars).isLessThanOrEqualTo(700)
    assertThat(result.exceedsSoftCap).isFalse()
  }

  @Test
  fun verySmallSoftCapKeepsItemStubsInsteadOfDroppingItems() {
    val result = AgentPromptContextEnvelopeFormatter.applySoftCap(
      items = listOf(
        contextItem(kindId = "snippet", title = "A", content = "x".repeat(200)),
        contextItem(kindId = "paths", title = "B", content = "y".repeat(200)),
      ),
      softCapChars = 200,
    )

    assertThat(result.items).hasSize(2)
    assertThat(result.items.count { item -> item.content == "[omitted due to soft cap]" }).isGreaterThan(0)
    assertThat(result.items)
      .allSatisfy { item ->
        assertThat(item.metadata[AgentPromptContextMetadataKeys.TRUNCATED]).isEqualTo("true")
      }
  }

  private fun contextItem(
    kindId: String,
    title: String,
    content: String,
  ): AgentPromptContextItem {
    return AgentPromptContextItem(
      kindId = kindId,
      title = title,
      content = content,
      metadata = mapOf(
        AgentPromptContextMetadataKeys.SOURCE to "test",
      ),
    )
  }
}
