// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend.appserver

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class CodexPromptSuggestionModelSettingsTest {
  //@Test
  //fun defaultsToGpt54AndEnabled() {
  //  assertThat(CodexPromptSuggestionModelSettings.getModel()).isEqualTo(DEFAULT_CODEX_PROMPT_SUGGESTION_MODEL)
  //  assertThat(CodexPromptSuggestionModelSettings.isDisabled()).isFalse()
  //}

  @Test
  fun offDisablesPolishingCaseInsensitively(@TestDisposable disposable: Disposable) {
    setSetting("  OFF  ", disposable)

    assertThat(CodexPromptSuggestionModelSettings.isDisabled()).isTrue()
    assertThat(CodexPromptSuggestionModelSettings.getModel()).isEqualTo(DEFAULT_CODEX_PROMPT_SUGGESTION_MODEL)
  }

  @Test
  fun blankValueFallsBackToDefault(@TestDisposable disposable: Disposable) {
    setSetting("   ", disposable)

    assertThat(CodexPromptSuggestionModelSettings.isDisabled()).isFalse()
    assertThat(CodexPromptSuggestionModelSettings.getModel()).isEqualTo(DEFAULT_CODEX_PROMPT_SUGGESTION_MODEL)
  }

  @Test
  fun customModelTrimsWhitespace(@TestDisposable disposable: Disposable) {
    setSetting("  local-codex  ", disposable)

    assertThat(CodexPromptSuggestionModelSettings.isDisabled()).isFalse()
    assertThat(CodexPromptSuggestionModelSettings.getModel()).isEqualTo("local-codex")
  }

  private fun setSetting(value: String, disposable: Disposable) {
    val advancedSettings = AdvancedSettings.getInstance() as AdvancedSettingsImpl
    advancedSettings.setSetting(CODEX_PROMPT_SUGGESTION_MODEL_SETTING_ID, value, disposable)
  }
}
