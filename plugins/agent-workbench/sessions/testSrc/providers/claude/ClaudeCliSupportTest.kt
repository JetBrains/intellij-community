// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.providers.claude

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClaudeCliSupportTest {
  @Test
  fun buildNewSessionCommandNormal() {
    assertThat(ClaudeCliSupport.buildNewSessionCommand(yolo = false))
      .containsExactly("claude")
  }

  @Test
  fun buildNewSessionCommandYolo() {
    assertThat(ClaudeCliSupport.buildNewSessionCommand(yolo = true))
      .containsExactly("claude", "--dangerously-skip-permissions")
  }

  @Test
  fun buildResumeCommand() {
    assertThat(ClaudeCliSupport.buildResumeCommand("sess-1"))
      .containsExactly("claude", "--resume", "sess-1")
  }
}
