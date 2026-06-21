// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.CodexThreadPage

internal suspend fun seedInitialVisibleThreads(
  initialPage: CodexThreadPage,
  minimumVisibleThreads: Int,
  loadNextPage: suspend (cursor: String) -> CodexThreadPage,
): CodexThreadPage {
  if (minimumVisibleThreads <= 0) return initialPage

  var mergedThreads = mergeCodexThreads(emptyList(), initialPage.threads)
  var nextCursor = initialPage.nextCursor
  val visitedCursors = LinkedHashSet<String>()

  while (mergedThreads.size < minimumVisibleThreads) {
    val cursor = nextCursor?.takeIf { it.isNotBlank() } ?: break
    if (!visitedCursors.add(cursor)) break

    val page = loadNextPage(cursor)
    val previousSize = mergedThreads.size
    mergedThreads = mergeCodexThreads(mergedThreads, page.threads)
    nextCursor = page.nextCursor

    if (mergedThreads.size == previousSize && nextCursor == cursor) break
  }

  return CodexThreadPage(
    threads = mergedThreads,
    nextCursor = nextCursor,
  )
}

private fun mergeCodexThreads(currentThreads: List<CodexThread>, additionalThreads: List<CodexThread>): List<CodexThread> {
  val merged = LinkedHashMap<String, CodexThread>()
  for (thread in currentThreads) {
    merged[thread.id] = thread
  }
  for (thread in additionalThreads) {
    val existing = merged[thread.id]
    if (existing == null || thread.updatedAt >= existing.updatedAt) {
      merged[thread.id] = thread
    }
  }
  return merged.values.sortedByDescending { it.updatedAt }
}

