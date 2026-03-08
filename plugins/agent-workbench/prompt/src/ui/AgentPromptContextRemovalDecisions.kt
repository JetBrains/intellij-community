// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import java.util.ArrayDeque

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
