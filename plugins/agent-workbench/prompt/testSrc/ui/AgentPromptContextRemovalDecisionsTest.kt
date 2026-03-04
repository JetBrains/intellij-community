// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

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

