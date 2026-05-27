// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
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
        taskDrafts = mapOf(PromptTargetMode.NEW_TASK.name to "prompt"),
        providerOptionsByProviderId = mapOf("codex" to setOf("plan_mode")),
      )
    )

    assertThat(service.loadContextRestoreSnapshot()).isEqualTo(snapshot)
  }

  @Test
  fun submittedPromptHistoryNormalizesDeduplicatesAndMovesLatestFirst() {
    val service = AgentPromptUiSessionStateService()

    service.saveSubmittedPromptHistoryEntry(historyEntry("first"))
    service.saveSubmittedPromptHistoryEntry(historyEntry("second\r\nline"))
    service.saveSubmittedPromptHistoryEntry(historyEntry(" first ", createdAtMs = 3))

    assertThat(service.loadPromptHistory().map { it.promptText }).containsExactly("first", "second\nline")
    assertThat(service.loadPromptHistory().first().createdAtMs).isEqualTo(3)
  }

  @Test
  fun submittedPromptHistoryIgnoresBlankPromptsAndCapsEntries() {
    val service = AgentPromptUiSessionStateService()

    service.saveSubmittedPromptHistoryEntry(historyEntry("   "))
    repeat(60) { index ->
      service.saveSubmittedPromptHistoryEntry(historyEntry("prompt-$index", createdAtMs = index.toLong()))
    }

    val history = service.loadPromptHistory()
    assertThat(history).hasSize(50)
    assertThat(history.first().promptText).isEqualTo("prompt-59")
    assertThat(history.last().promptText).isEqualTo("prompt-10")
  }

  @Test
  fun savedPromptsRoundTripNormalizesDeduplicatesAndMovesLatestFirst() {
    val service = AgentPromptUiSessionStateService()

    service.savePersistentPrompt("first", createdAtMs = 1)
    service.savePersistentPrompt("second\r\nline", createdAtMs = 2)
    service.savePersistentPrompt(" first ", createdAtMs = 3)

    val savedPrompts = service.loadSavedPrompts()
    assertThat(savedPrompts.map { it.promptText }).containsExactly("first", "second\nline")
    assertThat(savedPrompts.first().createdAtMs).isEqualTo(3)

    val reloaded = AgentPromptUiSessionStateService()
    reloaded.loadState(service.state)

    assertThat(reloaded.loadSavedPrompts()).isEqualTo(savedPrompts)
  }

  @Test
  fun savedPromptsIgnoreBlankPromptsAndCapEntries() {
    val service = AgentPromptUiSessionStateService()

    service.savePersistentPrompt("   ")
    repeat(60) { index ->
      service.savePersistentPrompt("prompt-$index", createdAtMs = index.toLong())
    }

    val savedPrompts = service.loadSavedPrompts()
    assertThat(savedPrompts).hasSize(50)
    assertThat(savedPrompts.first().promptText).isEqualTo("prompt-59")
    assertThat(savedPrompts.last().promptText).isEqualTo("prompt-10")
  }

  @Test
  fun removePersistentPromptRemovesSavedPromptByNormalizedText() {
    val service = AgentPromptUiSessionStateService()

    service.savePersistentPrompt("first")
    service.savePersistentPrompt("second")

    service.removePersistentPrompt(" first ")

    assertThat(service.loadSavedPrompts().map { it.promptText }).containsExactly("second")
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

  private fun historyEntry(promptText: String, createdAtMs: Long = 1): AgentPromptHistoryEntry {
    return AgentPromptHistoryEntry(
      promptText = promptText,
      createdAtMs = createdAtMs,
      providerId = "codex",
      targetMode = PromptTargetMode.NEW_TASK,
      launchMode = "STANDARD",
    )
  }
}
