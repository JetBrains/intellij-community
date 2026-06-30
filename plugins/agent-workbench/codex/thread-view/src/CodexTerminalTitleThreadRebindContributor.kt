// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.thread.view

import com.intellij.agent.workbench.thread.view.AgentThreadViewTerminalTitleThreadRebindContributor
import com.intellij.agent.workbench.thread.view.AgentThreadViewTerminalTitleThreadRebindSignal
import java.util.Locale

internal class CodexTerminalTitleThreadRebindContributor : AgentThreadViewTerminalTitleThreadRebindContributor {
  override fun extractThreadId(applicationTitle: String?): String? {
    return extractThreadSignal(applicationTitle)?.threadId
  }

  override fun extractThreadSignal(applicationTitle: String?): AgentThreadViewTerminalTitleThreadRebindSignal? {
    val normalizedTitle = applicationTitle?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val match = CODEX_THREAD_ID_IN_TERMINAL_TITLE_REGEX.find(normalizedTitle) ?: return null
    val threadId = match.value.lowercase(Locale.ROOT)
    return AgentThreadViewTerminalTitleThreadRebindSignal(
      threadId = threadId,
      threadTitle = extractCodexThreadTitle(normalizedTitle, match),
    )
  }
}

private fun extractCodexThreadTitle(
  terminalTitle: String,
  threadIdMatch: MatchResult,
): String? {
  if (threadIdMatch.range.first != 0) {
    return null
  }
  return terminalTitle
    .substring(threadIdMatch.range.last + 1)
    .trim()
    .removePrefix("|")
    .trim()
    .takeIf { it.isNotBlank() }
}

private val CODEX_THREAD_ID_IN_TERMINAL_TITLE_REGEX = Regex(
  "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"
)
