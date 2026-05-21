// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.common

const val CLAUDE_ARCHIVED_THREAD_TITLE_PREFIX: String = "[archived] "

data class ClaudeThreadTitleState(
  @JvmField val title: String,
  @JvmField val archived: Boolean,
)

fun resolveClaudeThreadTitleState(rawTitle: String, sessionId: String): ClaudeThreadTitleState {
  val archived = isClaudeArchivedThreadTitle(rawTitle)
  val visibleTitle = normalizeClaudeStoredThreadTitle(stripClaudeArchivedThreadTitlePrefix(rawTitle))
                     ?: defaultClaudeThreadTitle(sessionId)
  return ClaudeThreadTitleState(title = visibleTitle, archived = archived)
}

fun buildClaudeArchivedThreadTitle(title: String, sessionId: String): String {
  return CLAUDE_ARCHIVED_THREAD_TITLE_PREFIX + resolveClaudeThreadTitleState(title, sessionId).title
}

fun isClaudeArchivedThreadTitle(title: String): Boolean {
  return title.startsWith(CLAUDE_ARCHIVED_THREAD_TITLE_PREFIX)
}

fun stripClaudeArchivedThreadTitlePrefix(title: String): String {
  return if (isClaudeArchivedThreadTitle(title)) title.removePrefix(CLAUDE_ARCHIVED_THREAD_TITLE_PREFIX) else title
}

fun normalizeClaudeStoredThreadTitle(value: String?): String? {
  val normalized = value
    ?.replace('\n', ' ')
    ?.replace('\r', ' ')
    ?.replace(Regex("\\s+"), " ")
    ?.trim()
    ?: return null
  return normalized.takeIf(String::isNotEmpty)
}

fun defaultClaudeThreadTitle(sessionId: String): String {
  return "Session ${sessionId.take(8)}"
}
