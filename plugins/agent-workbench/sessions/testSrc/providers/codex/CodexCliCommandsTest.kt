// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.providers.codex

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CodexCliCommandsTest {
  @Test
  fun buildResumeCommand() {
    assertThat(CodexCliCommands.buildResumeCommand("thread-1"))
      .containsExactly("codex", "resume", "thread-1")
  }
}
