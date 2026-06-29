// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.platform

import com.intellij.agent.workbench.engine.core.ThreadApprovalStatus
import com.intellij.agent.workbench.engine.core.ThreadToolApproval
import com.intellij.agent.workbench.engine.core.ThreadToolCall
import com.intellij.agent.workbench.engine.core.ThreadToolOutput
import com.intellij.agent.workbench.engine.ui.AgentAcpToolCallPresenter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentAcpToolCallPresenterTest {
  @Test
  fun `renders title and approval in compact header state`() {
    val presentation = AgentAcpToolCallPresenter.create(
      ThreadToolCall(
        id = "tool-1",
        title = "Web search: Portugal in December",
        status = "completed",
        output = listOf(ThreadToolOutput(text = "{\"referenceCount\":1}")),
        approval = ThreadToolApproval(toolCallId = "tool-1", status = ThreadApprovalStatus.Approved),
      ),
    )

    assertThat(presentation.title).isEqualTo("Web search: Portugal in December")
    assertThat(presentation.status).isEqualTo("Completed - Approved")
    assertThat(presentation.details).isEmpty()
  }

  @Test
  fun `keeps useful tool output visible as monospace detail`() {
    val presentation = AgentAcpToolCallPresenter.create(
      ThreadToolCall(
        id = "tool-1",
        title = "Read file",
        output = listOf(ThreadToolOutput(stream = "stdout", text = "first line\nsecond line")),
      ),
    )

    val detail = presentation.details.single()
    assertThat(detail.text).isEqualTo("Output:\nfirst line\nsecond line")
    assertThat(detail.monospace).isTrue()
  }

  @Test
  fun `shows approval reason when available`() {
    val presentation = AgentAcpToolCallPresenter.create(
      ThreadToolCall(
        id = "tool-1",
        title = "Run command",
        approval = ThreadToolApproval(
          toolCallId = "tool-1",
          status = ThreadApprovalStatus.Rejected,
          reason = "User denied execution",
        ),
      ),
    )

    assertThat(presentation.status).isEqualTo("Rejected")
    val detail = presentation.details.single()
    assertThat(detail.text).isEqualTo("Approval reason: User denied execution")
    assertThat(detail.monospace).isFalse()
  }

  @Test
  fun `detects compact counter metadata`() {
    assertThat(AgentAcpToolCallPresenter.isCompactCounterMetadata("{\"referenceCount\":1}")).isTrue()
    assertThat(AgentAcpToolCallPresenter.isCompactCounterMetadata("{\"totalMatches\":0}")).isTrue()
    assertThat(AgentAcpToolCallPresenter.isCompactCounterMetadata("{\"totalMatches\":161,\"truncated\":true}")).isTrue()
    assertThat(AgentAcpToolCallPresenter.isCompactCounterMetadata("{\"result\":\"done\"}")).isFalse()
    assertThat(AgentAcpToolCallPresenter.isCompactCounterMetadata("{\"referenceCount\":1}\nmore")).isFalse()
  }

  @Test
  fun `hides completed compact metadata tool call from transcript`() {
    val toolCall = ThreadToolCall(
      id = "tool-1",
      title = "Web search",
      status = "completed",
      output = listOf(ThreadToolOutput(text = "{\"referenceCount\":1}")),
      approval = ThreadToolApproval(toolCallId = "tool-1", status = ThreadApprovalStatus.Approved),
      complete = true,
    )

    assertThat(AgentAcpToolCallPresenter.shouldRenderInTranscript(toolCall)).isFalse()
  }

  @Test
  fun `hides completed structured count output tool call from transcript`() {
    val toolCall = ThreadToolCall(
      id = "tool-1",
      title = "grep",
      status = "completed",
      output = listOf(ThreadToolOutput(text = "{\"totalMatches\":161,\"truncated\":true}")),
      complete = true,
    )

    assertThat(AgentAcpToolCallPresenter.shouldRenderInTranscript(toolCall)).isFalse()
  }

  @Test
  fun `keeps running tool call visible in transcript`() {
    val toolCall = ThreadToolCall(
      id = "tool-1",
      title = "Web search",
      status = "running",
      complete = false,
    )

    assertThat(AgentAcpToolCallPresenter.shouldRenderInTranscript(toolCall)).isTrue()
  }

  @Test
  fun `keeps requested approval visible in transcript`() {
    val toolCall = ThreadToolCall(
      id = "tool-1",
      title = "Run command",
      status = "waiting",
      approval = ThreadToolApproval(toolCallId = "tool-1", status = ThreadApprovalStatus.Requested),
      complete = true,
    )

    assertThat(AgentAcpToolCallPresenter.shouldRenderInTranscript(toolCall)).isTrue()
  }

  @Test
  fun `keeps failed tool call visible in transcript`() {
    val toolCall = ThreadToolCall(
      id = "tool-1",
      title = "Run command",
      status = "failed",
      output = listOf(ThreadToolOutput(stream = "stderr", text = "permission denied")),
      complete = true,
    )

    assertThat(AgentAcpToolCallPresenter.shouldRenderInTranscript(toolCall)).isTrue()
    assertThat(AgentAcpToolCallPresenter.create(toolCall).details.single().text).isEqualTo("Output:\npermission denied")
  }

  @Test
  fun `hides successful completed output from transcript`() {
    val toolCall = ThreadToolCall(
      id = "tool-1",
      title = "Read file",
      status = "completed",
      output = listOf(ThreadToolOutput(stream = "stdout", text = "important result")),
      complete = true,
    )

    assertThat(AgentAcpToolCallPresenter.shouldRenderInTranscript(toolCall)).isFalse()
    assertThat(AgentAcpToolCallPresenter.create(toolCall).details).isEmpty()
  }
}
