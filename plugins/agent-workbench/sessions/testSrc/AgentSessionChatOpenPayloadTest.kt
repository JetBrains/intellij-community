// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.agent.workbench.sessions.service.resolveAgentSessionChatOpenPayload
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionChatOpenPayloadTest {
  @Test
  fun resolvesBaseThreadPayload() {
    val thread = AgentSessionThread(
      id = "thread-1",
      title = "Parent title",
      updatedAt = 1,
      archived = false,
      provider = AgentSessionProvider.CODEX,
    )

    val payload = resolveAgentSessionChatOpenPayload(
      thread = thread,
      subAgent = null,
      shellCommandOverride = null,
    )

    assertThat(payload.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "thread-1"))
    assertThat(payload.runtimeThreadId).isEqualTo("thread-1")
    assertThat(payload.shellCommand).containsExactly("codex", "resume", "thread-1")
    assertThat(payload.threadTitle).isEqualTo("Parent title")
    assertThat(payload.subAgentId).isNull()
  }

  @Test
  fun resolvesSubAgentPayloadWithSubAgentRuntimeThreadIdAndTitle() {
    val thread = AgentSessionThread(
      id = "thread-1",
      title = "Parent title",
      updatedAt = 1,
      archived = false,
      provider = AgentSessionProvider.CODEX,
    )
    val subAgent = AgentSubAgent(id = "sub-1", name = "Sub-agent label")

    val payload = resolveAgentSessionChatOpenPayload(
      thread = thread,
      subAgent = subAgent,
      shellCommandOverride = null,
    )

    assertThat(payload.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "thread-1"))
    assertThat(payload.runtimeThreadId).isEqualTo("sub-1")
    assertThat(payload.shellCommand).containsExactly("codex", "resume", "sub-1")
    assertThat(payload.threadTitle).isEqualTo("Sub-agent label")
    assertThat(payload.subAgentId).isEqualTo("sub-1")
  }

  @Test
  fun resolvesSubAgentPayloadWithSubAgentIdWhenNameBlank() {
    val thread = AgentSessionThread(
      id = "thread-1",
      title = "Parent title",
      updatedAt = 1,
      archived = false,
      provider = AgentSessionProvider.CODEX,
    )
    val subAgent = AgentSubAgent(id = "sub-1", name = "")

    val payload = resolveAgentSessionChatOpenPayload(
      thread = thread,
      subAgent = subAgent,
      shellCommandOverride = null,
    )

    assertThat(payload.threadTitle).isEqualTo("sub-1")
  }

  @Test
  fun keepsShellCommandOverrideWhenProvided() {
    val thread = AgentSessionThread(
      id = "thread-1",
      title = "Parent title",
      updatedAt = 1,
      archived = false,
      provider = AgentSessionProvider.CODEX,
    )
    val subAgent = AgentSubAgent(id = "sub-1", name = "Sub-agent label")

    val payload = resolveAgentSessionChatOpenPayload(
      thread = thread,
      subAgent = subAgent,
      shellCommandOverride = listOf("custom", "resume", "sub-1"),
    )

    assertThat(payload.shellCommand).containsExactly("custom", "resume", "sub-1")
  }
}
