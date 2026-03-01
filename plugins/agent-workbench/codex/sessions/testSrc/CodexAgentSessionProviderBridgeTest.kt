// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CodexAgentSessionProviderBridgeTest {
  private val bridge = CodexAgentSessionProviderBridge()

  @Test
  fun buildResumeCommand() {
    assertThat(bridge.buildResumeCommand("thread-1"))
      .containsExactly("codex", "resume", "thread-1")
  }

  @Test
  fun buildNewEntryCommand() {
    assertThat(bridge.buildNewEntryCommand())
      .containsExactly("codex")
  }

  @Test
  fun buildNewSessionCommand() {
    assertThat(bridge.buildNewSessionCommand(AgentSessionLaunchMode.STANDARD))
      .containsExactly("codex")
    assertThat(bridge.buildNewSessionCommand(AgentSessionLaunchMode.YOLO))
      .containsExactly("codex", "--full-auto")
  }

  @Test
  fun supportsUnarchiveThread() {
    assertThat(bridge.supportsUnarchiveThread).isTrue()
  }

  @Test
  fun createNewSessionReturnsPendingLaunchSpec() {
    runBlocking(Dispatchers.Default) {
      val standard = bridge.createNewSession(path = "/work/project", mode = AgentSessionLaunchMode.STANDARD)
      assertThat(standard.sessionId).isNull()
      assertThat(standard.command).containsExactly("codex")

      val yolo = bridge.createNewSession(path = "/work/project", mode = AgentSessionLaunchMode.YOLO)
      assertThat(yolo.sessionId).isNull()
      assertThat(yolo.command).containsExactly("codex", "--full-auto")
    }
  }

}
