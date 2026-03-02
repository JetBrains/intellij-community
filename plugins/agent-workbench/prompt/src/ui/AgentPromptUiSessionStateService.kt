// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

internal enum class PromptTargetMode {
  NEW_TASK,
  EXISTING_TASK,
}

internal enum class PromptSendMode {
  SEND_NOW,
}

internal data class AgentPromptUiDraft(
  @JvmField val promptText: String = "",
  @JvmField val providerId: String? = null,
  @JvmField val targetMode: PromptTargetMode = PromptTargetMode.NEW_TASK,
  @JvmField val sendMode: PromptSendMode = PromptSendMode.SEND_NOW,
  @JvmField val existingTaskSearch: String = "",
  @JvmField val selectedExistingTaskId: String? = null,
)

internal data class AgentPromptUiState(
  @JvmField val draft: AgentPromptUiDraft = AgentPromptUiDraft(),
)

@Service(Service.Level.PROJECT)
@State(name = "AgentPromptUiState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class AgentPromptUiSessionStateService
  : SerializablePersistentStateComponent<AgentPromptUiState>(AgentPromptUiState()) {

  fun loadDraft(): AgentPromptUiDraft {
    return state.draft
  }

  fun saveDraft(newDraft: AgentPromptUiDraft) {
    updateState { current -> current.copy(draft = newDraft) }
  }

  fun clearDraft() {
    updateState { current -> current.copy(draft = AgentPromptUiDraft()) }
  }
}
