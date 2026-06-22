// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.common

import com.intellij.platform.ai.agent.common.session.claudeMenuCommands
import com.intellij.platform.ai.agent.common.session.isClaudeMenuCommandPrompt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class ClaudeMenuCommandsTest {
  @Test
  fun renameIsRecognizedAsMenuCommand() {
    assertThat(claudeMenuCommands()).contains("/rename")
    assertThat("/rename Archived thread".isClaudeMenuCommandPrompt()).isTrue()
  }
}
