// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.common

import com.intellij.agent.workbench.common.session.claudeMenuCommands
import com.intellij.agent.workbench.common.session.isClaudeMenuCommandPrompt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClaudeMenuCommandsTest {
  @Test
  fun renameIsRecognizedAsMenuCommand() {
    assertThat(claudeMenuCommands()).contains("/rename")
    assertThat("/rename Archived thread".isClaudeMenuCommandPrompt()).isTrue()
  }
}
