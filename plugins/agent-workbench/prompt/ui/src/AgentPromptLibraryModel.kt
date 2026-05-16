// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceEntry
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil

private const val MAX_PROMPT_LIBRARY_SAVED_PROMPT_ENTRIES = 50

internal fun buildPromptLibraryRows(
  promptFiles: List<AgentPromptReusableSourceEntry>,
  savedPromptEntries: List<AgentPromptSavedPromptEntry>,
  historyEntries: List<AgentPromptHistoryEntry>,
): List<PromptLibraryRow> {
  val rowsByPromptText = LinkedHashMap<String, PromptLibraryRowBuilder>()

  fun <T> collectEntries(
    entries: Iterable<T>,
    promptText: (T) -> String,
    updateRow: (PromptLibraryRowBuilder, T) -> Unit,
  ) {
    entries.forEach { entry ->
      val normalizedPromptText = normalizeAgentPromptText(promptText(entry))
      if (normalizedPromptText.isNotBlank()) {
        val builder = rowsByPromptText.getOrPut(normalizedPromptText) { PromptLibraryRowBuilder(normalizedPromptText) }
        updateRow(builder, entry)
      }
    }
  }

  collectEntries(savedPromptEntries, AgentPromptSavedPromptEntry::promptText) { _, _ -> }
  collectEntries(promptFiles, AgentPromptReusableSourceEntry::insertText) { builder, entry ->
    if (builder.promptFileEntry == null) {
      builder.promptFileEntry = entry
    }
  }
  collectEntries(historyEntries, AgentPromptHistoryEntry::promptText) { builder, entry ->
    if (builder.historyEntry == null) {
      builder.historyEntry = entry
    }
  }

  return rowsByPromptText.values.map { builder -> builder.toRow() }
}

internal class PromptLibraryState(
  private val promptFiles: List<AgentPromptReusableSourceEntry>,
  private val historyEntries: List<AgentPromptHistoryEntry>,
  savedPromptEntries: List<AgentPromptSavedPromptEntry>,
) {
  private var savedPromptEntriesSnapshot = savedPromptEntries

  val savedPromptEntries: List<AgentPromptSavedPromptEntry>
    get() = savedPromptEntriesSnapshot

  fun rows(): List<PromptLibraryRow> {
    return buildPromptLibraryRows(
      promptFiles = promptFiles,
      savedPromptEntries = savedPromptEntriesSnapshot,
      historyEntries = historyEntries,
    )
  }

  fun resolveEntry(row: PromptLibraryRow): PromptLibraryEntry? {
    return row.resolveEntry(savedPromptEntriesSnapshot)
  }

  fun markSaved(savedPromptEntry: AgentPromptSavedPromptEntry) {
    val normalizedPromptText = normalizeAgentPromptText(savedPromptEntry.promptText)
    if (normalizedPromptText.isBlank()) {
      return
    }

    savedPromptEntriesSnapshot = buildList {
      add(savedPromptEntry)
      savedPromptEntriesSnapshot
        .asSequence()
        .filter { entry -> normalizeAgentPromptText(entry.promptText) != normalizedPromptText }
        .take(MAX_PROMPT_LIBRARY_SAVED_PROMPT_ENTRIES - 1)
        .forEach(::add)
    }
  }

  fun markRemoved(promptText: String) {
    val normalizedPromptText = normalizeAgentPromptText(promptText)
    if (normalizedPromptText.isBlank()) {
      return
    }

    savedPromptEntriesSnapshot = savedPromptEntriesSnapshot.filter { entry ->
      normalizeAgentPromptText(entry.promptText) != normalizedPromptText
    }
  }
}

internal data class PromptLibraryRow(
  @JvmField val normalizedPromptText: String,
  @JvmField val promptFileEntry: AgentPromptReusableSourceEntry? = null,
  @JvmField val historyEntry: AgentPromptHistoryEntry? = null,
) {
  fun resolveEntry(savedPromptEntries: List<AgentPromptSavedPromptEntry>): PromptLibraryEntry? {
    val savedPromptEntry = savedPromptEntries.firstOrNull { entry ->
      normalizeAgentPromptText(entry.promptText) == normalizedPromptText
    }
    return when {
      savedPromptEntry != null -> PromptLibraryEntry.SavedPrompt(savedPromptEntry)
      promptFileEntry != null -> PromptLibraryEntry.PromptFile(promptFileEntry)
      historyEntry != null -> PromptLibraryEntry.RecentPrompt(historyEntry)
      else -> null
    }
  }
}

internal sealed interface PromptLibraryEntry {
  val insertText: String
  val searchText: @NlsSafe String

  data class SavedPrompt(val savedPromptEntry: AgentPromptSavedPromptEntry) : PromptLibraryEntry {
    override val insertText: String
      get() = savedPromptEntry.promptText
    override val searchText: String
      get() = savedPromptEntry.promptText
  }

  data class PromptFile(val sourceEntry: AgentPromptReusableSourceEntry) : PromptLibraryEntry {
    override val insertText: String
      get() = sourceEntry.insertText
    override val searchText: String
      get() = listOfNotNull(sourceEntry.label, sourceEntry.description, sourceEntry.sourcePath).joinToString("\n")
  }

  data class RecentPrompt(val historyEntry: AgentPromptHistoryEntry) : PromptLibraryEntry {
    override val insertText: String
      get() = historyEntry.promptText
    override val searchText: String
      get() = historyEntry.promptText
  }
}

internal fun PromptLibraryEntry.displayText(): @NlsSafe String = when (this) {
  is PromptLibraryEntry.SavedPrompt -> compactPromptPreview(savedPromptEntry.promptText)
  is PromptLibraryEntry.PromptFile -> sourceEntry.label
  is PromptLibraryEntry.RecentPrompt -> compactPromptPreview(historyEntry.promptText)
}

internal fun PromptLibraryEntry.secondaryText(): @NlsSafe String? = when (this) {
  is PromptLibraryEntry.SavedPrompt -> null
  is PromptLibraryEntry.PromptFile -> {
    val description = sourceEntry.description?.takeIf(String::isNotBlank)?.let { StringUtil.first(it, 80, true) }
    listOfNotNull(AgentPromptBundle.message("popup.prompt.library.type.prompt.file"), description).joinToString("  ")
  }
  is PromptLibraryEntry.RecentPrompt -> {
    val details = listOfNotNull(historyEntry.providerId, historyEntry.targetMode.name.lowercase().replace('_', ' ')).joinToString(" · ")
    listOfNotNull(AgentPromptBundle.message("popup.prompt.library.type.recent"), details.takeIf(String::isNotBlank)).joinToString("  ")
  }
}

private class PromptLibraryRowBuilder(
  @JvmField val normalizedPromptText: String,
) {
  var promptFileEntry: AgentPromptReusableSourceEntry? = null
  var historyEntry: AgentPromptHistoryEntry? = null

  fun toRow(): PromptLibraryRow {
    return PromptLibraryRow(
      normalizedPromptText = normalizedPromptText,
      promptFileEntry = promptFileEntry,
      historyEntry = historyEntry,
    )
  }
}

private fun compactPromptPreview(promptText: String): String {
  val singleLine = promptText.replace('\n', ' ').trim()
  return StringUtil.first(singleLine, 100, true)
}
