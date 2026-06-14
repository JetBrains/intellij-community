// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.dynatrace.hash4j.hashing.HashValue128
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import kotlinx.serialization.Serializable

private const val MAX_PROMPT_HISTORY_ENTRIES = 50
private const val MAX_SAVED_PROMPT_ENTRIES = 50

@Serializable
internal enum class PromptTargetMode {
  NEW_TASK,
  EXISTING_TASK,
}

@Serializable
internal enum class PromptSendMode {
  SEND_NOW,
}

@Serializable
internal data class AgentPromptUiDraft(
  @JvmField val promptText: String = "",
  @JvmField val providerId: String? = null,
  @JvmField val targetMode: PromptTargetMode = PromptTargetMode.NEW_TASK,
  @JvmField val sendMode: PromptSendMode = PromptSendMode.SEND_NOW,
  @JvmField val existingTaskSearch: String = "",
  @JvmField val selectedExistingTaskId: String? = null,
  @JvmField val taskDrafts: Map<String, String> = emptyMap(),
  @JvmField val providerOptionsByProviderId: Map<String, Set<String>> = emptyMap(),
  @JvmField val containerModeEnabled: Boolean = false,
)

@Serializable
internal data class AgentPromptHistoryEntry(
  @JvmField val promptText: String,
  @JvmField val createdAtMs: Long,
  @JvmField val providerId: String? = null,
  @JvmField val targetMode: PromptTargetMode = PromptTargetMode.NEW_TASK,
  @JvmField val launchMode: String? = null,
)

@Serializable
internal data class AgentPromptSavedPromptEntry(
  @JvmField val promptText: String,
  @JvmField val createdAtMs: Long,
)

internal data class AgentPromptUiContextRestoreSnapshot(
  @JvmField val contextFingerprint: HashValue128? = null,
  @JvmField val removedContextItemIds: List<String> = emptyList(),
  @JvmField val manualContextItemsBySourceId: Map<String, List<AgentPromptContextItem>> = emptyMap(),
)

@Serializable
internal data class AgentPromptUiState(
  @JvmField val draft: AgentPromptUiDraft = AgentPromptUiDraft(),
  @JvmField val promptHistory: List<AgentPromptHistoryEntry> = emptyList(),
  @JvmField val savedPrompts: List<AgentPromptSavedPromptEntry> = emptyList(),
  @JvmField val autoClose: Boolean = true,
)

@Service(Service.Level.PROJECT)
@State(name = "AgentPromptUiState", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
internal class AgentPromptUiSessionStateService
  : SerializablePersistentStateComponent<AgentPromptUiState>(AgentPromptUiState()) {
  // Runtime-only snapshot: intentionally not persisted in AgentPromptUiState.
  private var contextRestoreSnapshot = AgentPromptUiContextRestoreSnapshot()

  var autoClose: Boolean
    get() = state.autoClose
    set(value) {
      updateState { state.copy(autoClose = value) }
    }

  fun loadDraft(): AgentPromptUiDraft {
    return state.draft
  }

  fun saveDraft(newDraft: AgentPromptUiDraft) {
    updateState { current -> current.copy(draft = newDraft) }
  }

  fun loadPromptHistory(): List<AgentPromptHistoryEntry> {
    return state.promptHistory
  }

  fun loadSavedPrompts(): List<AgentPromptSavedPromptEntry> {
    return state.savedPrompts
  }

  fun saveSubmittedPromptHistoryEntry(entry: AgentPromptHistoryEntry) {
    val normalizedPrompt = normalizeAgentPromptText(entry.promptText)
    if (normalizedPrompt.isBlank()) {
      return
    }
    val normalizedEntry = entry.copy(promptText = normalizedPrompt)
    updateState { current ->
      val updatedHistory = buildList {
        add(normalizedEntry)
        current.promptHistory
          .asSequence()
          .filter { historyEntry -> normalizeAgentPromptText(historyEntry.promptText) != normalizedPrompt }
          .take(MAX_PROMPT_HISTORY_ENTRIES - 1)
          .forEach(::add)
      }
      current.copy(promptHistory = updatedHistory)
    }
  }

  fun savePersistentPrompt(promptText: String, createdAtMs: Long = System.currentTimeMillis()): AgentPromptSavedPromptEntry? {
    val normalizedPrompt = normalizeAgentPromptText(promptText)
    if (normalizedPrompt.isBlank()) {
      return null
    }

    val savedEntry = AgentPromptSavedPromptEntry(
      promptText = normalizedPrompt,
      createdAtMs = createdAtMs,
    )
    updateState { current ->
      val updatedSavedPrompts = buildList {
        add(savedEntry)
        current.savedPrompts
          .asSequence()
          .filter { savedPrompt -> normalizeAgentPromptText(savedPrompt.promptText) != normalizedPrompt }
          .take(MAX_SAVED_PROMPT_ENTRIES - 1)
          .forEach(::add)
      }
      current.copy(savedPrompts = updatedSavedPrompts)
    }
    return savedEntry
  }

  fun removePersistentPrompt(promptText: String) {
    val normalizedPrompt = normalizeAgentPromptText(promptText)
    if (normalizedPrompt.isBlank()) {
      return
    }

    updateState { current ->
      current.copy(
        savedPrompts = current.savedPrompts.filter { savedPrompt ->
          normalizeAgentPromptText(savedPrompt.promptText) != normalizedPrompt
        }
      )
    }
  }

  fun loadContextRestoreSnapshot(): AgentPromptUiContextRestoreSnapshot {
    return contextRestoreSnapshot
  }

  fun saveContextRestoreSnapshot(newSnapshot: AgentPromptUiContextRestoreSnapshot) {
    contextRestoreSnapshot = newSnapshot
  }

  fun clearDraft() {
    updateState { current -> current.copy(draft = AgentPromptUiDraft()) }
    contextRestoreSnapshot = AgentPromptUiContextRestoreSnapshot()
  }
}

internal fun normalizeAgentPromptText(promptText: String): String {
  return promptText.replace("\r\n", "\n").replace('\r', '\n').trim()
}
