// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.context.AgentPromptScreenshotContextItem.deleteScreenshotContextFileIfPresent
import com.intellij.agent.workbench.prompt.context.MANUAL_PROJECT_PATHS_SOURCE_ID
import com.intellij.agent.workbench.prompt.context.buildManualPathsContextItem
import com.intellij.agent.workbench.prompt.context.extractCurrentPaths
import com.intellij.agent.workbench.prompt.context.removeManualPathSelection
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import java.util.ArrayDeque

internal object AgentPromptContextRemovalDecisions {

  internal fun resolveContextEntriesAfterRemoval(
    entries: List<ContextEntry>,
    removedEntryId: String,
  ): List<ContextEntry> {
    val removedEntry = entries.firstOrNull { entry -> entry.id == removedEntryId } ?: return entries
    val removedLogicalItemIds = removedEntry.logicalItemId
      ?.let { logicalItemId -> collectContextHierarchyRemovalIds(entries = entries, rootItemId = logicalItemId) }
      .orEmpty()
    return entries.filterNot { entry ->
      entry.id == removedEntryId || (entry.logicalItemId != null && entry.logicalItemId in removedLogicalItemIds)
    }
  }

  internal fun resolveManualContextItemsAfterRemoval(
    manualItemsBySourceId: Map<String, AgentPromptContextItem>,
    removedEntry: ContextEntry,
    projectPath: String?,
  ): Map<String, AgentPromptContextItem> {
    val sourceId = removedEntry.manualSourceId ?: return manualItemsBySourceId
    val currentItem = manualItemsBySourceId[sourceId] ?: return manualItemsBySourceId

    val updatedItems = LinkedHashMap(manualItemsBySourceId)
    if (sourceId != MANUAL_PROJECT_PATHS_SOURCE_ID) {
      updatedItems.remove(sourceId)
      return updatedItems
    }

    val currentSelection = extractCurrentPaths(currentItem)
    val remainingSelection = removeManualPathSelection(currentSelection, extractCurrentPaths(removedEntry.item))
    if (remainingSelection == currentSelection) {
      return manualItemsBySourceId
    }
    if (remainingSelection.isEmpty()) {
      updatedItems.remove(sourceId)
      return updatedItems
    }

    val updatedItem = buildManualPathsContextItem(remainingSelection)
    if (normalizeContextItemForProject(updatedItem, projectPath) == null) {
      updatedItems.remove(sourceId)
    }
    else {
      updatedItems[sourceId] = updatedItem
    }
    return updatedItems
  }

  internal fun removeManualContextItemsAfterExplicitRemoval(
    manualItemsBySourceId: Map<String, AgentPromptContextItem>,
    removedEntry: ContextEntry,
    projectPath: String?,
  ): Map<String, AgentPromptContextItem> {
    val updatedItems = resolveManualContextItemsAfterRemoval(
      manualItemsBySourceId = manualItemsBySourceId,
      removedEntry = removedEntry,
      projectPath = projectPath,
    )
    if (updatedItems != manualItemsBySourceId) {
      deleteScreenshotContextFileIfPresent(removedEntry.item)
    }
    return updatedItems
  }

  private fun collectContextHierarchyRemovalIds(
    entries: List<ContextEntry>,
    rootItemId: String,
  ): Set<String> {
    val queue = ArrayDeque<String>()
    val removedItemIds = LinkedHashSet<String>()
    queue.addLast(rootItemId)
    while (queue.isNotEmpty()) {
      val currentItemId = queue.removeFirst()
      if (!removedItemIds.add(currentItemId)) {
        continue
      }
      entries.forEach { entry ->
        val childItemId = entry.logicalItemId ?: return@forEach
        if (entry.logicalParentItemId == currentItemId && childItemId !in removedItemIds) {
          queue.addLast(childItemId)
        }
      }
    }
    return removedItemIds
  }
}
