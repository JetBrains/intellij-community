// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend.appserver

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md

import com.intellij.openapi.options.advanced.AdvancedSettings

internal const val CODEX_PROMPT_SUGGESTION_MODEL_SETTING_ID: String = "agent.workbench.codex.prompt.suggestion.model"
internal const val DEFAULT_CODEX_PROMPT_SUGGESTION_MODEL: String = "gpt-5.4"
internal const val DEFAULT_CODEX_PROMPT_SUGGESTION_REASONING_EFFORT: String = "low"

private const val DISABLED_CODEX_PROMPT_SUGGESTION_MODEL: String = "off"

internal object CodexPromptSuggestionModelSettings {
  fun getModel(): String {
    val configuredValue = readConfiguredValue()
    return if (configuredValue.isBlank() || configuredValue.equals(DISABLED_CODEX_PROMPT_SUGGESTION_MODEL, ignoreCase = true)) {
      DEFAULT_CODEX_PROMPT_SUGGESTION_MODEL
    }
    else {
      configuredValue
    }
  }

  fun isDisabled(): Boolean {
    return readConfiguredValue().equals(DISABLED_CODEX_PROMPT_SUGGESTION_MODEL, ignoreCase = true)
  }

  fun getReasoningEffort(): String {
    return DEFAULT_CODEX_PROMPT_SUGGESTION_REASONING_EFFORT
  }

  private fun readConfiguredValue(): String {
    return AdvancedSettings.getString(CODEX_PROMPT_SUGGESTION_MODEL_SETTING_ID).trim()
  }
}
