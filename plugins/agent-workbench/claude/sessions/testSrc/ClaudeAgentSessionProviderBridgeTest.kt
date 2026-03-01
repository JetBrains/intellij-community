// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClaudeAgentSessionProviderBridgeTest {
  private val bridge = ClaudeAgentSessionProviderBridge()

  @Test
  fun buildNewEntryCommand() {
    assertThat(bridge.buildNewEntryCommand())
      .containsExactly("claude")
  }

  @Test
  fun buildResumeCommand() {
    assertThat(bridge.buildResumeCommand("session-1"))
      .containsExactly("claude", "--resume", "session-1")
  }

  @Test
  fun buildYoloCommand() {
    assertThat(bridge.buildNewSessionCommand(AgentSessionLaunchMode.YOLO))
      .containsExactly("claude", "--dangerously-skip-permissions")
  }
}
