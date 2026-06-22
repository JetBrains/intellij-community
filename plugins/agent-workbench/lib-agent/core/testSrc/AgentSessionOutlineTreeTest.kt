// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.core

import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineItemKind
import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineTreeRecord
import com.intellij.platform.ai.agent.core.session.buildAgentSessionOutlineTree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentSessionOutlineTreeTest {
  @Test
  fun `hidden bookkeeping records promote visible children`() {
    val items = buildAgentSessionOutlineTree(
      listOf(
        record(id = "user", parentId = null, kind = AgentSessionOutlineItemKind.USER_PROMPT, title = "User", timestampMs = 1_000L),
        record(id = "label", parentId = "user", visible = false, timestampMs = 2_000L),
        record(id = "assistant",
               parentId = "label",
               kind = AgentSessionOutlineItemKind.ASSISTANT_RESPONSE,
               title = "Done",
               timestampMs = 3_000L),
      )
    )

    assertThat(items).hasSize(1)
    assertThat(items.single().id).isEqualTo("user")
    assertThat(items.single().children.map { it.id }).containsExactly("assistant")
  }

  @Test
  fun `missing and self parents become timestamp ordered roots`() {
    val items = buildAgentSessionOutlineTree(
      listOf(
        record(id = "late", parentId = "missing", title = "Late", timestampMs = 3_000L),
        record(id = "early", parentId = "early", title = "Early", timestampMs = 1_000L),
      )
    )

    assertThat(items.map { it.id }).containsExactly("early", "late")
  }

  private fun record(
    id: String,
    parentId: String?,
    kind: AgentSessionOutlineItemKind = AgentSessionOutlineItemKind.METADATA,
    title: String = "Metadata",
    timestampMs: Long?,
    visible: Boolean = true,
  ): AgentSessionOutlineTreeRecord {
    return AgentSessionOutlineTreeRecord(
      id = id,
      parentId = parentId,
      kind = kind,
      title = title,
      timestampMs = timestampMs,
      visible = visible,
    )
  }
}
