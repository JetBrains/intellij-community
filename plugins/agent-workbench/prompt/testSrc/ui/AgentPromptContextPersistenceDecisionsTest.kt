// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayload
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayloadValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentPromptContextPersistenceDecisionsTest {
  @Test
  fun matchingFingerprintReappliesRemovedHierarchy() {
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
    val fingerprint = computeContextFingerprint(entries.map { entry -> entry.item })

    val restored = applyDraftContextRemovals(
      entries = entries,
      currentFingerprint = fingerprint,
      draftFingerprint = fingerprint,
      removedLogicalItemIds = listOf("editor.file"),
    )

    assertThat(restored.map { entry -> entry.id }).containsExactly("paths")
  }

  @Test
  fun mismatchedFingerprintDoesNotApplyRemovals() {
    val entries = listOf(
      contextEntry(entryId = "file", logicalItemId = "editor.file", rendererId = AgentPromptContextRendererIds.FILE),
      contextEntry(entryId = "snippet", logicalItemId = "editor.snippet", rendererId = AgentPromptContextRendererIds.SNIPPET),
    )
    val fingerprint = computeContextFingerprint(entries.map { entry -> entry.item })

    val restored = applyDraftContextRemovals(
      entries = entries,
      currentFingerprint = fingerprint,
      draftFingerprint = Hashing.xxh3_128().hashCharsTo128Bits("different"),
      removedLogicalItemIds = listOf("editor.file"),
    )

    assertThat(restored).containsExactlyElementsOf(entries)
  }

  @Test
  fun removedLogicalIdCollectionSkipsNonLogicalEntries() {
    val entries = listOf(
      contextEntry(entryId = "legacy", logicalItemId = null, rendererId = AgentPromptContextRendererIds.FILE),
      contextEntry(entryId = "snippet", logicalItemId = "editor.snippet", rendererId = AgentPromptContextRendererIds.SNIPPET),
    )
    val remaining = resolveContextEntriesAfterRemoval(entries, removedEntryId = "legacy")

    assertThat(collectRemovedLogicalItemIds(entries, remaining)).isEmpty()
  }

  @Test
  fun removedLogicalIdCollectionIncludesRemovedHierarchy() {
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
    )
    val remaining = resolveContextEntriesAfterRemoval(entries, removedEntryId = "file")

    assertThat(collectRemovedLogicalItemIds(entries, remaining))
      .containsExactly("editor.file", "editor.symbol", "editor.snippet")
  }

  @Test
  fun normalizeRemovedContextItemIdsFiltersBlanksAndDuplicates() {
    val normalized = normalizeRemovedContextItemIds(
      listOf(
        "  editor.file  ",
        "",
        "editor.file",
        "  ",
        "editor.snippet",
      )
    )

    assertThat(normalized).containsExactly("editor.file", "editor.snippet")
  }

  @Test
  fun fingerprintChangesWhenContextBodyChanges() {
    val base = listOf(
      contextItem(
        rendererId = AgentPromptContextRendererIds.SNIPPET,
        title = "Snippet",
        body = "return 41",
      )
    )
    val changed = listOf(
      contextItem(
        rendererId = AgentPromptContextRendererIds.SNIPPET,
        title = "Snippet",
        body = "return 42",
      )
    )

    assertThat(computeContextFingerprint(base)).isNotEqualTo(computeContextFingerprint(changed))
  }

  @Test
  fun fingerprintIsStableAcrossPayloadObjectKeyOrdering() {
    val payloadA = AgentPromptPayloadValue.Obj(
      linkedMapOf(
        "b" to AgentPromptPayload.str("2"),
        "a" to AgentPromptPayload.str("1"),
      )
    )
    val payloadB = AgentPromptPayloadValue.Obj(
      linkedMapOf(
        "a" to AgentPromptPayload.str("1"),
        "b" to AgentPromptPayload.str("2"),
      )
    )

    val first = listOf(contextItem(rendererId = AgentPromptContextRendererIds.PATHS, title = "Paths", body = "x", payload = payloadA))
    val second = listOf(contextItem(rendererId = AgentPromptContextRendererIds.PATHS, title = "Paths", body = "x", payload = payloadB))

    assertThat(computeContextFingerprint(first)).isEqualTo(computeContextFingerprint(second))
  }

  private fun contextEntry(
    entryId: String,
    logicalItemId: String?,
    rendererId: String,
    logicalParentItemId: String? = null,
  ): ContextEntry {
    return ContextEntry(
      item = contextItem(
        rendererId = rendererId,
        title = "Context",
        body = entryId,
        logicalItemId = logicalItemId,
        logicalParentItemId = logicalParentItemId,
      ),
      id = entryId,
    )
  }

  private fun contextItem(
    rendererId: String,
    title: String,
    body: String,
    logicalItemId: String? = null,
    logicalParentItemId: String? = null,
    payload: AgentPromptPayloadValue = AgentPromptPayloadValue.Obj.EMPTY,
  ): AgentPromptContextItem {
    return AgentPromptContextItem(
      rendererId = rendererId,
      title = title,
      body = body,
      payload = payload,
      itemId = logicalItemId,
      parentItemId = logicalParentItemId,
      source = "test",
    )
  }
}
