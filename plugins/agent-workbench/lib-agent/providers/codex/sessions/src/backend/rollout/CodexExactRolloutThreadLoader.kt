// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions.backend.rollout

import com.intellij.platform.ai.agent.codex.sessions.backend.CodexBackendThread
import java.nio.file.Path

internal class CodexExactRolloutThreadLoader(
  private val parseRollout: (Path) -> ParsedRolloutThread? = CodexRolloutParser()::parse,
) {
  fun loadThreads(
    cwdFilter: String?,
    threadIds: Set<String>,
    aggregateThreadIds: Set<String>,
    rolloutPaths: Set<String>,
  ): Map<String, CodexBackendThread> {
    if (threadIds.isEmpty() || rolloutPaths.isEmpty()) {
      return emptyMap()
    }

    val parsedThreads = rolloutPaths.asSequence()
      .map { rolloutPath -> runCatching { parseRollout(Path.of(rolloutPath)) }.getOrNull() }
      .mapNotNull { parsedThread -> parsedThread }
      .toList()
    if (parsedThreads.isEmpty()) {
      return emptyMap()
    }

    val mergedThreads = mergeParsedRolloutThreadsByCwd(parsedThreads)
    val candidateThreads = if (cwdFilter == null) {
      mergedThreads.values.asSequence().flatten().toList()
    }
    else {
      mergedThreads[cwdFilter].orEmpty()
    }
    val exactThreadsById = parsedThreads
      .asSequence()
      .filter { parsedThread -> cwdFilter == null || parsedThread.normalizedCwd == cwdFilter }
      .associate { parsedThread -> parsedThread.thread.thread.id to parsedThread }
    val mergedThreadsById = candidateThreads.associateBy { thread -> thread.thread.id }
    return threadIds.mapNotNull { threadId ->
      val resolvedThread = when {
        threadId in aggregateThreadIds -> mergedThreadsById[threadId] ?: exactThreadsById[threadId]?.thread
        else -> exactThreadsById[threadId]?.thread ?: mergedThreadsById[threadId]
      } ?: return@mapNotNull null
      threadId to resolvedThread
    }.toMap(LinkedHashMap())
  }
}
