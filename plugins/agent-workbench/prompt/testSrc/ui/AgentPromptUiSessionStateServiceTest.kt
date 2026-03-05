// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.dynatrace.hash4j.hashing.Hashing
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentPromptUiSessionStateServiceTest {
  @Test
  fun contextRestoreSnapshotRoundTripWithinSession() {
    val service = AgentPromptUiSessionStateService()
    val snapshot = AgentPromptUiContextRestoreSnapshot(
      contextFingerprint = Hashing.xxh3_128().hashCharsTo128Bits("context"),
      removedContextItemIds = listOf("editor.file", "editor.symbol"),
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
        codexPlanModeEnabled = false,
      )
    )
    service.saveContextRestoreSnapshot(
      AgentPromptUiContextRestoreSnapshot(
        contextFingerprint = Hashing.xxh3_128().hashCharsTo128Bits("context"),
        removedContextItemIds = listOf("editor.file"),
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
    )
    service.saveContextRestoreSnapshot(snapshot)

    service.saveDraft(
      AgentPromptUiDraft(
        promptText = "prompt",
        providerId = "codex",
        targetMode = PromptTargetMode.NEW_TASK,
        existingTaskSearch = "",
        selectedExistingTaskId = null,
        codexPlanModeEnabled = true,
      )
    )

    assertThat(service.loadContextRestoreSnapshot()).isEqualTo(snapshot)
  }
}

