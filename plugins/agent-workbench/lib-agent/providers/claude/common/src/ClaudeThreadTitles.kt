// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.claude.common

import com.intellij.platform.ai.agent.core.session.AGENT_SESSION_ARCHIVED_THREAD_TITLE_PREFIX
import com.intellij.platform.ai.agent.core.session.AgentSessionArchivedTitleState
import com.intellij.platform.ai.agent.core.session.buildAgentSessionArchivedThreadTitle
import com.intellij.platform.ai.agent.core.session.isAgentSessionArchivedThreadTitle
import com.intellij.platform.ai.agent.core.session.normalizeAgentSessionStoredThreadTitle
import com.intellij.platform.ai.agent.core.session.resolveAgentSessionArchivedTitleState
import com.intellij.platform.ai.agent.core.session.stripAgentSessionArchivedThreadTitlePrefix

@Suppress("unused")
const val CLAUDE_ARCHIVED_THREAD_TITLE_PREFIX: String = AGENT_SESSION_ARCHIVED_THREAD_TITLE_PREFIX

data class ClaudeThreadTitleState(
  @JvmField val title: String,
  @JvmField val archived: Boolean,
)

fun resolveClaudeThreadTitleState(rawTitle: String, sessionId: String): ClaudeThreadTitleState {
  return resolveAgentSessionArchivedTitleState(rawTitle, defaultClaudeThreadTitle(sessionId)).toClaudeThreadTitleState()
}

fun buildClaudeArchivedThreadTitle(title: String, sessionId: String): String {
  return buildAgentSessionArchivedThreadTitle(title, defaultClaudeThreadTitle(sessionId))
}

fun isClaudeArchivedThreadTitle(title: String): Boolean {
  return isAgentSessionArchivedThreadTitle(title)
}

@Suppress("unused")
fun stripClaudeArchivedThreadTitlePrefix(title: String): String {
  return stripAgentSessionArchivedThreadTitlePrefix(title)
}

fun normalizeClaudeStoredThreadTitle(value: String?): String? {
  return normalizeAgentSessionStoredThreadTitle(value)
}

fun defaultClaudeThreadTitle(sessionId: String): String {
  return "Session ${sessionId.take(8)}"
}

private fun AgentSessionArchivedTitleState.toClaudeThreadTitleState(): ClaudeThreadTitleState {
  return ClaudeThreadTitleState(title = title, archived = archived)
}
