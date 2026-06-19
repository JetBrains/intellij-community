// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.prompt.suggestions

import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexPromptSuggestionModelSettingsTest {
  @Test
  fun offDisablesPolishingCaseInsensitively() {
    CodexPromptSuggestionModelSettings.withConfiguredValueForTest("  OFF  ") {
      assertThat(CodexPromptSuggestionModelSettings.isDisabled()).isTrue()
      assertThat(CodexPromptSuggestionModelSettings.getModel()).isEqualTo(DEFAULT_CODEX_PROMPT_SUGGESTION_MODEL)
    }
  }

  @Test
  fun blankValueFallsBackToDefault() {
    CodexPromptSuggestionModelSettings.withConfiguredValueForTest("   ") {
      assertThat(CodexPromptSuggestionModelSettings.isDisabled()).isFalse()
      assertThat(CodexPromptSuggestionModelSettings.getModel()).isEqualTo(DEFAULT_CODEX_PROMPT_SUGGESTION_MODEL)
    }
  }

  @Test
  fun customModelTrimsWhitespace() {
    CodexPromptSuggestionModelSettings.withConfiguredValueForTest("  local-codex  ") {
      assertThat(CodexPromptSuggestionModelSettings.isDisabled()).isFalse()
      assertThat(CodexPromptSuggestionModelSettings.getModel()).isEqualTo("local-codex")
    }
  }
}
