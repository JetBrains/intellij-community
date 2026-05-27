// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceEntry
import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptLibraryModelTest {
  @Test
  fun recentPromptResolvesAsSavedAfterSave() {
    val historyEntry = historyEntry("  3+3?  ")
    val row = buildPromptLibraryRows(
      promptFiles = emptyList(),
      savedPromptEntries = emptyList(),
      historyEntries = listOf(historyEntry),
    ).single()

    assertThat(row.resolveEntry(emptyList())).isInstanceOf(PromptLibraryEntry.RecentPrompt::class.java)

    val entry = row.resolveEntry(listOf(savedPrompt("3+3?", createdAtMs = 2)))

    assertThat(entry).isInstanceOf(PromptLibraryEntry.SavedPrompt::class.java)
    assertThat((entry as PromptLibraryEntry.SavedPrompt).savedPromptEntry.createdAtMs).isEqualTo(2)
  }

  @Test
  fun savedPromptFallsBackToRecentAfterRemove() {
    val row = buildPromptLibraryRows(
      promptFiles = emptyList(),
      savedPromptEntries = listOf(savedPrompt("Fix tests")),
      historyEntries = listOf(historyEntry(" Fix tests ")),
    ).single()

    val entry = row.resolveEntry(emptyList())

    assertThat(entry).isInstanceOf(PromptLibraryEntry.RecentPrompt::class.java)
    assertThat((entry as PromptLibraryEntry.RecentPrompt).historyEntry.promptText).isEqualTo(" Fix tests ")
  }

  @Test
  fun savedPromptFallsBackToPromptFileBeforeRecentAfterRemove() {
    val row = buildPromptLibraryRows(
      promptFiles = listOf(promptFile("Review", "Review changes")),
      savedPromptEntries = listOf(savedPrompt("Review changes")),
      historyEntries = listOf(historyEntry("Review changes")),
    ).single()

    val entry = row.resolveEntry(emptyList())

    assertThat(entry).isInstanceOf(PromptLibraryEntry.PromptFile::class.java)
    assertThat((entry as PromptLibraryEntry.PromptFile).sourceEntry.label).isEqualTo("Review")
  }

  @Test
  fun savedOnlyPromptDisappearsAfterRemove() {
    val row = buildPromptLibraryRows(
      promptFiles = emptyList(),
      savedPromptEntries = listOf(savedPrompt("Only saved")),
      historyEntries = emptyList(),
    ).single()

    assertThat(row.resolveEntry(emptyList())).isNull()
  }

  @Test
  fun promptRowsKeepSavedPromptFileRecentOrderAndDeduplicateByNormalizedText() {
    val rows = buildPromptLibraryRows(
      promptFiles = listOf(
        promptFile("Prompt File", "Prompt file text"),
        promptFile("Duplicate File", "Saved text"),
      ),
      savedPromptEntries = listOf(savedPrompt(" Saved text ")),
      historyEntries = listOf(
        historyEntry("Prompt file text"),
        historyEntry("Recent text"),
      ),
    )

    assertThat(rows.map { row -> row.normalizedPromptText }).containsExactly("Saved text", "Prompt file text", "Recent text")
    assertThat(rows[0].resolveEntry(listOf(savedPrompt("Saved text")))).isInstanceOf(PromptLibraryEntry.SavedPrompt::class.java)
    assertThat(rows[1].resolveEntry(emptyList())).isInstanceOf(PromptLibraryEntry.PromptFile::class.java)
    assertThat(rows[2].resolveEntry(emptyList())).isInstanceOf(PromptLibraryEntry.RecentPrompt::class.java)
  }

  private fun savedPrompt(promptText: String, createdAtMs: Long = 1): AgentPromptSavedPromptEntry {
    return AgentPromptSavedPromptEntry(
      promptText = promptText,
      createdAtMs = createdAtMs,
    )
  }

  private fun historyEntry(promptText: String): AgentPromptHistoryEntry {
    return AgentPromptHistoryEntry(
      promptText = promptText,
      createdAtMs = 1,
      providerId = "claude",
      targetMode = PromptTargetMode.NEW_TASK,
    )
  }

  private fun promptFile(label: String, insertText: String): AgentPromptReusableSourceEntry {
    return AgentPromptReusableSourceEntry(
      id = "prompt-file:$label",
      label = label,
      insertText = insertText,
      kind = AgentPromptReusableSourceKind.PROMPT_FILE,
    )
  }
}
