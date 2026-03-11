// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.dynatrace.hash4j.hashing.HashValue128
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import kotlinx.serialization.Serializable

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
    @JvmField val planModeEnabled: Boolean = true,
    @JvmField val taskDrafts: Map<String, String> = emptyMap(),
    @JvmField val providerOptionsByProviderId: Map<String, Set<String>> = emptyMap(),
)

internal data class AgentPromptUiContextRestoreSnapshot(
    @JvmField val contextFingerprint: HashValue128? = null,
    @JvmField val removedContextItemIds: List<String> = emptyList(),
    @JvmField val manualContextItemsBySourceId: Map<String, AgentPromptContextItem> = emptyMap(),
)

@Serializable
internal data class AgentPromptUiProviderPreferences(
    @JvmField val providerId: String? = null,
    @JvmField val launchModeName: String? = null,
    @JvmField val providerOptionsByProviderId: Map<String, Set<String>> = emptyMap(),
)

@Serializable
internal data class AgentPromptUiState(
    @JvmField val draft: AgentPromptUiDraft = AgentPromptUiDraft(),
)

@Service(Service.Level.PROJECT)
@State(name = "AgentPromptUiState", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
internal class AgentPromptUiSessionStateService
    : SerializablePersistentStateComponent<AgentPromptUiState>(AgentPromptUiState()) {
    // Runtime-only snapshot: intentionally not persisted in AgentPromptUiState.
    private var contextRestoreSnapshot = AgentPromptUiContextRestoreSnapshot()

    fun loadDraft(): AgentPromptUiDraft {
        return state.draft
    }

    fun saveDraft(newDraft: AgentPromptUiDraft) {
        updateState { current -> current.copy(draft = newDraft) }
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
