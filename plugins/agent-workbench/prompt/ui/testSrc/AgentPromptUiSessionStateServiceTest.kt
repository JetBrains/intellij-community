// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentPromptUiSessionStateServiceTest {
    @Test
    fun loadStateRoundTripPersistsDraftButNotContextRestoreSnapshot() {
        val original = AgentPromptUiSessionStateService()
        val draft = AgentPromptUiDraft(
            promptText = "fix",
            providerId = "codex",
            targetMode = PromptTargetMode.EXISTING_TASK,
            existingTaskSearch = "query",
            selectedExistingTaskId = "task-1",
            planModeEnabled = false,
            taskDrafts = mapOf(PromptTargetMode.NEW_TASK.name to "fix"),
            providerOptionsByProviderId = mapOf("codex" to emptySet()),
        )
        val snapshot = AgentPromptUiContextRestoreSnapshot(
            contextFingerprint = Hashing.xxh3_128().hashCharsTo128Bits("context"),
            removedContextItemIds = listOf("editor.file"),
            manualContextItemsBySourceId = mapOf("manual.vcs.commits" to listOf(manualContextItem())),
        )

        original.saveDraft(draft)
        original.saveContextRestoreSnapshot(snapshot)

        val reloaded = AgentPromptUiSessionStateService()
        reloaded.loadState(original.state)

        assertThat(reloaded.loadDraft()).isEqualTo(draft)
        assertThat(reloaded.loadContextRestoreSnapshot()).isEqualTo(AgentPromptUiContextRestoreSnapshot())
    }

    @Test
    fun contextRestoreSnapshotRoundTripWithinSession() {
        val service = AgentPromptUiSessionStateService()
        val snapshot = AgentPromptUiContextRestoreSnapshot(
            contextFingerprint = Hashing.xxh3_128().hashCharsTo128Bits("context"),
            removedContextItemIds = listOf("editor.file", "editor.symbol"),
            manualContextItemsBySourceId = mapOf("manual.vcs.commits" to listOf(manualContextItem())),
        )

        service.saveContextRestoreSnapshot(snapshot)

        assertThat(service.loadContextRestoreSnapshot()).isEqualTo(snapshot)
    }

    @Test
    fun clearDraftResetsDraftAndContextRestoreSnapshot() {
        val service = AgentPromptUiSessionStateService()
        service.saveDraft(
            AgentPromptUiDraft(
                promptText = "fix",
                providerId = "codex",
                targetMode = PromptTargetMode.EXISTING_TASK,
                existingTaskSearch = "query",
                selectedExistingTaskId = "task-1",
                planModeEnabled = false,
                taskDrafts = mapOf(PromptTargetMode.NEW_TASK.name to "fix"),
                providerOptionsByProviderId = mapOf("codex" to emptySet()),
            )
        )
        service.saveContextRestoreSnapshot(
            AgentPromptUiContextRestoreSnapshot(
                contextFingerprint = Hashing.xxh3_128().hashCharsTo128Bits("context"),
                removedContextItemIds = listOf("editor.file"),
                manualContextItemsBySourceId = mapOf("manual.vcs.commits" to listOf(manualContextItem())),
            )
        )

        service.clearDraft()

        assertThat(service.loadDraft()).isEqualTo(AgentPromptUiDraft())
        assertThat(service.loadContextRestoreSnapshot()).isEqualTo(AgentPromptUiContextRestoreSnapshot())
    }

    @Test
    fun saveDraftDoesNotModifyContextRestoreSnapshot() {
        val service = AgentPromptUiSessionStateService()
        val snapshot = AgentPromptUiContextRestoreSnapshot(
            contextFingerprint = Hashing.xxh3_128().hashCharsTo128Bits("context"),
            removedContextItemIds = listOf("editor.file"),
            manualContextItemsBySourceId = mapOf("manual.vcs.commits" to listOf(manualContextItem())),
        )
        service.saveContextRestoreSnapshot(snapshot)

        service.saveDraft(
            AgentPromptUiDraft(
                promptText = "prompt",
                providerId = "codex",
                targetMode = PromptTargetMode.NEW_TASK,
                existingTaskSearch = "",
                selectedExistingTaskId = null,
                planModeEnabled = true,
                taskDrafts = mapOf(PromptTargetMode.NEW_TASK.name to "prompt"),
                providerOptionsByProviderId = mapOf("codex" to setOf("plan_mode")),
            )
        )

        assertThat(service.loadContextRestoreSnapshot()).isEqualTo(snapshot)
    }

    private fun manualContextItem(): AgentPromptContextItem {
        return AgentPromptContextItem(
            rendererId = AgentPromptContextRendererIds.VCS_COMMITS,
            title = "Picked Commits",
            body = "abc12345",
            itemId = "manual.vcs.commits",
            source = "manualVcs",
        )
    }
}
