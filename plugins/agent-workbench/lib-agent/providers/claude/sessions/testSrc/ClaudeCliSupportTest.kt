// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class ClaudeCliSupportTest {
  @Test
  fun buildNewSessionCommandNormal() {
    assertThat(ClaudeCliSupport.buildNewSessionCommand(yolo = false, sessionId = "session-1"))
      .containsExactly("claude", "--session-id", "session-1")
  }

  @Test
  fun buildNewSessionCommandYolo() {
    assertThat(ClaudeCliSupport.buildNewSessionCommand(yolo = true, sessionId = "session-1"))
      .containsExactly("claude", "--dangerously-skip-permissions", "--session-id", "session-1")
  }

  @Test
  fun buildResumeCommand() {
    assertThat(ClaudeCliSupport.buildResumeCommand("sess-1"))
      .containsExactly("claude", "--resume", "sess-1")
  }

  @Test
  fun buildNewSessionCommandUsesProvidedAbsoluteExecutable() {
    assertThat(ClaudeCliSupport.buildNewSessionCommand(yolo = false, sessionId = "session-1", executable = "/opt/tools/claude"))
      .containsExactly("/opt/tools/claude", "--session-id", "session-1")
  }

  @Test
  fun buildResumeCommandUsesProvidedAbsoluteExecutable() {
    assertThat(ClaudeCliSupport.buildResumeCommand("sess-1", executable = "/opt/tools/claude"))
      .containsExactly("/opt/tools/claude", "--resume", "sess-1")
  }
}
