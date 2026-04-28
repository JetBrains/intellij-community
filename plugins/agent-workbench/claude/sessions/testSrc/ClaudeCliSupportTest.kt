// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClaudeCliSupportTest {
  @Test
  fun buildNewSessionCommandNormal() {
    assertThat(ClaudeCliSupport.buildNewSessionCommand(yolo = false))
      .containsExactly("claude", "--permission-mode", "default")
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

  @Test
  fun buildNewSessionCommandUsesProvidedAbsoluteExecutable() {
    assertThat(ClaudeCliSupport.buildNewSessionCommand(yolo = false, executable = "/opt/tools/claude"))
      .containsExactly("/opt/tools/claude", "--permission-mode", "default")
  }

  @Test
  fun buildResumeCommandUsesProvidedAbsoluteExecutable() {
    assertThat(ClaudeCliSupport.buildResumeCommand("sess-1", executable = "/opt/tools/claude"))
      .containsExactly("/opt/tools/claude", "--resume", "sess-1")
  }
}
