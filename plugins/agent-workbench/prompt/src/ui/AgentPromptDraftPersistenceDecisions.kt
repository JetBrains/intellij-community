// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPaletteInitialPrompt

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

internal fun synchronizeExtensionDraftState(
  state: AgentPromptTaskDraftState,
  promptKind: String?,
  synchronizePrompt: (AgentPromptPaletteInitialPrompt) -> AgentPromptPaletteInitialPrompt,
): AgentPromptTaskDraftState {
  val synchronizedPersistedPrompt = synchronizePrompt(AgentPromptPaletteInitialPrompt(promptKind, state.persistedUserText))
  val synchronizedLiveText = if (state.liveText == state.persistedUserText) {
    synchronizedPersistedPrompt.content
  }
  else {
    synchronizePrompt(AgentPromptPaletteInitialPrompt(promptKind, state.liveText)).content
  }
  return state.copy(
    liveText = synchronizedLiveText,
    persistedUserText = synchronizedPersistedPrompt.content,
  )
}

internal object AgentPromptExtensionDraftDecisions {
  fun taskKey(taskKeyPrefix: String, draftKind: String?): String {
    return if (draftKind.isNullOrBlank()) taskKeyPrefix else "$taskKeyPrefix:$draftKind"
  }

  fun matchesTaskKey(taskKeyPrefix: String, taskKey: String): Boolean {
    return taskKey == taskKeyPrefix || taskKey.startsWith("$taskKeyPrefix:")
  }

  fun persistTaskDrafts(
    taskKeyPrefix: String,
    taskStates: Map<String, AgentPromptTaskDraftState>,
    classifyPromptDraftKind: (String) -> String?,
  ): HashMap<String, String> {
    val persistedDrafts = HashMap<String, String>()
    taskStates.entries
      .asSequence()
      .filter { (taskKey, _) -> matchesTaskKey(taskKeyPrefix, taskKey) }
      .forEach { (_, state) ->
        persistedDrafts[taskKey(taskKeyPrefix, classifyPromptDraftKind(state.persistedUserText))] = state.persistedUserText
      }
    return persistedDrafts
  }
}
