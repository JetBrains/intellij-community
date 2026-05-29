// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend.rollout

import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import java.nio.file.Path

internal class CodexExactRolloutThreadLoader(
  private val parseRollout: (Path) -> ParsedRolloutThread? = CodexRolloutParser()::parse,
) {
  fun loadThreads(cwdFilter: String, threadIds: Set<String>, rolloutPaths: Set<String>): Map<String, CodexBackendThread> {
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

    return mergeParsedRolloutThreadsByCwd(parsedThreads)[cwdFilter]
      .orEmpty()
      .asSequence()
      .filter { thread -> thread.matchesRequestedThreadIds(threadIds) }
      .associateBy { thread -> thread.thread.id }
  }
}
