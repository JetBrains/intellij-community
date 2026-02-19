// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.sessions.AgentSessionLaunchMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
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
  fun buildNewSessionCommandThrows() {
    assertThrows(IllegalStateException::class.java) {
      bridge.buildNewSessionCommand(AgentSessionLaunchMode.STANDARD)
    }
  }
}
