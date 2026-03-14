// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md
// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md

internal data class AgentPromptTaskDraftState(
  @JvmField val liveText: String = "",
  @JvmField val persistedUserText: String = "",
  @JvmField val hasTransientSuggestion: Boolean = false,
)

internal fun restoredTaskPromptDraftState(text: String): AgentPromptTaskDraftState {
  return AgentPromptTaskDraftState(
    liveText = text,
    persistedUserText = text,
  )
}

internal fun applySuggestedPromptToDraftState(
  state: AgentPromptTaskDraftState,
  suggestionText: String,
): AgentPromptTaskDraftState {
  return state.copy(
    liveText = suggestionText,
    hasTransientSuggestion = true,
  )
}

internal fun applyUserEditToDraftState(
  state: AgentPromptTaskDraftState,
  promptText: String,
): AgentPromptTaskDraftState {
  return state.copy(
    liveText = promptText,
    persistedUserText = promptText,
    hasTransientSuggestion = false,
  )
}

internal fun syncLivePromptTextForDraftState(
  state: AgentPromptTaskDraftState,
  promptText: String,
): AgentPromptTaskDraftState {
  return state.copy(liveText = promptText)
}
