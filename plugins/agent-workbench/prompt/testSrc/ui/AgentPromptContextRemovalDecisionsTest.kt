// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.context.MANUAL_PROJECT_PATHS_SOURCE_ID
import com.intellij.agent.workbench.prompt.context.ManualPathSelectionEntry
import com.intellij.agent.workbench.prompt.context.buildManualPathsContextItem
import com.intellij.agent.workbench.prompt.context.extractCurrentPaths
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentPromptContextRemovalDecisionsTest {
  @Test
  fun removingParentRemovesAllDescendantsRecursively() {
    val entries = listOf(
      contextEntry(entryId = "file", logicalItemId = "editor.file", rendererId = AgentPromptContextRendererIds.FILE),
      contextEntry(
        entryId = "symbol",
        logicalItemId = "editor.symbol",
        logicalParentItemId = "editor.file",
        rendererId = AgentPromptContextRendererIds.SYMBOL,
      ),
      contextEntry(
        entryId = "snippet",
        logicalItemId = "editor.snippet",
        logicalParentItemId = "editor.symbol",
        rendererId = AgentPromptContextRendererIds.SNIPPET,
      ),
      contextEntry(entryId = "paths", logicalItemId = "projectView.selection", rendererId = AgentPromptContextRendererIds.PATHS),
    )

    val remaining = resolveContextEntriesAfterRemoval(entries, removedEntryId = "file")

    assertThat(remaining.map { it.id }).containsExactly("paths")
  }

  @Test
  fun removingChildKeepsParentAndSiblings() {
    val entries = listOf(
      contextEntry(entryId = "file", logicalItemId = "editor.file", rendererId = AgentPromptContextRendererIds.FILE),
      contextEntry(
        entryId = "symbol",
        logicalItemId = "editor.symbol",
        logicalParentItemId = "editor.file",
        rendererId = AgentPromptContextRendererIds.SYMBOL,
      ),
      contextEntry(
        entryId = "snippet",
        logicalItemId = "editor.snippet",
        logicalParentItemId = "editor.file",
        rendererId = AgentPromptContextRendererIds.SNIPPET,
      ),
      contextEntry(entryId = "paths", logicalItemId = "projectView.selection", rendererId = AgentPromptContextRendererIds.PATHS),
    )

    val remaining = resolveContextEntriesAfterRemoval(entries, removedEntryId = "symbol")

    assertThat(remaining.map { it.id }).containsExactly("file", "snippet", "paths")
  }

  @Test
  fun unknownEntryIdLeavesListUnchanged() {
    val entries = listOf(
      contextEntry(entryId = "file", logicalItemId = "editor.file", rendererId = AgentPromptContextRendererIds.FILE),
      contextEntry(entryId = "snippet", logicalItemId = "editor.snippet", rendererId = AgentPromptContextRendererIds.SNIPPET),
    )

    val remaining = resolveContextEntriesAfterRemoval(entries, removedEntryId = "missing")

    assertThat(remaining).containsExactlyElementsOf(entries)
  }

  @Test
  fun removingEntryWithoutLogicalIdRemovesOnlyClickedEntry() {
    val entries = listOf(
      contextEntry(entryId = "legacy", logicalItemId = null, rendererId = AgentPromptContextRendererIds.FILE),
      contextEntry(
        entryId = "snippet",
        logicalItemId = "editor.snippet",
        logicalParentItemId = "editor.file",
        rendererId = AgentPromptContextRendererIds.SNIPPET,
      ),
      contextEntry(entryId = "paths", logicalItemId = "projectView.selection", rendererId = AgentPromptContextRendererIds.PATHS),
    )

    val remaining = resolveContextEntriesAfterRemoval(entries, removedEntryId = "legacy")

    assertThat(remaining.map { it.id }).containsExactly("snippet", "paths")
  }

  @Test
  fun removingManualProjectPathEntryKeepsSiblingPathsInStoredSource() {
    val keepPath = "/repo/project/keep.txt"
    val removePath = "/repo/project/remove.txt"
    val manualItems = linkedMapOf(
      MANUAL_PROJECT_PATHS_SOURCE_ID to buildManualPathsContextItem(
        listOf(
          ManualPathSelectionEntry(path = keepPath, isDirectory = false),
          ManualPathSelectionEntry(path = removePath, isDirectory = false),
        )
      )
    )
    val removedEntry = materializeVisibleContextEntries(emptyList(), manualItems, null)
      .single { entry -> entry.id == manualPathContextEntryId(MANUAL_PROJECT_PATHS_SOURCE_ID, removePath) }

    val updatedItems = resolveManualContextItemsAfterRemoval(manualItems, removedEntry, projectPath = null)

    assertThat(updatedItems.keys).containsExactly(MANUAL_PROJECT_PATHS_SOURCE_ID)
    assertThat(extractCurrentPaths(updatedItems.getValue(MANUAL_PROJECT_PATHS_SOURCE_ID))).containsExactly(
      ManualPathSelectionEntry(path = keepPath, isDirectory = false),
    )
  }

  @Test
  fun removingLastVisibleManualProjectPathClearsStoredSourceEvenWhenHiddenProjectRootRemains() {
    val projectPath = "/repo/project"
    val filePath = "/repo/project/src/Main.kt"
    val manualItems = linkedMapOf(
      MANUAL_PROJECT_PATHS_SOURCE_ID to buildManualPathsContextItem(
        listOf(
          ManualPathSelectionEntry(path = projectPath, isDirectory = true),
          ManualPathSelectionEntry(path = filePath, isDirectory = false),
        )
      )
    )
    val removedEntry = materializeVisibleContextEntries(emptyList(), manualItems, projectPath)
      .single { entry -> entry.id == manualPathContextEntryId(MANUAL_PROJECT_PATHS_SOURCE_ID, filePath) }

    val updatedItems = resolveManualContextItemsAfterRemoval(manualItems, removedEntry, projectPath = projectPath)

    assertThat(updatedItems).isEmpty()
  }

  @Test
  fun removingOtherManualSourceDropsWholeSource() {
    val sourceId = "manual.vcs.commits"
    val manualItem = AgentPromptContextItem(
      rendererId = AgentPromptContextRendererIds.VCS_COMMITS,
      title = "Commits",
      body = "abc12345",
      source = "manualVcs",
    )
    val removedEntry = ContextEntry(
      item = manualItem,
      id = "manual:$sourceId",
      origin = ContextEntryOrigin.MANUAL,
      manualSourceId = sourceId,
    )

    val updatedItems = resolveManualContextItemsAfterRemoval(mapOf(sourceId to manualItem), removedEntry, projectPath = null)

    assertThat(updatedItems).isEmpty()
  }

  private fun contextEntry(
    entryId: String,
    logicalItemId: String?,
    rendererId: String,
    logicalParentItemId: String? = null,
  ): ContextEntry {
    return ContextEntry(
      item = AgentPromptContextItem(
        rendererId = rendererId,
        title = "Context",
        body = entryId,
        itemId = logicalItemId,
        parentItemId = logicalParentItemId,
        source = "test",
      ),
      id = entryId,
    )
  }
}
