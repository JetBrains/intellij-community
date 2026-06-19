// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.common.session

const val AGENT_SESSION_ARCHIVED_THREAD_TITLE_PREFIX: String = "[archived] "

data class AgentSessionArchivedTitleState(
  @JvmField val title: String,
  @JvmField val archived: Boolean,
)

fun resolveAgentSessionArchivedTitleState(rawTitle: String, defaultTitle: String): AgentSessionArchivedTitleState {
  val archived = isAgentSessionArchivedThreadTitle(rawTitle)
  val visibleTitle = normalizeAgentSessionStoredThreadTitle(stripAgentSessionArchivedThreadTitlePrefix(rawTitle))
                     ?: defaultTitle
  return AgentSessionArchivedTitleState(title = visibleTitle, archived = archived)
}

fun buildAgentSessionArchivedThreadTitle(title: String, defaultTitle: String): String {
  return AGENT_SESSION_ARCHIVED_THREAD_TITLE_PREFIX + resolveAgentSessionArchivedTitleState(title, defaultTitle).title
}

fun isAgentSessionArchivedThreadTitle(title: String): Boolean {
  return title.startsWith(AGENT_SESSION_ARCHIVED_THREAD_TITLE_PREFIX)
}

fun stripAgentSessionArchivedThreadTitlePrefix(title: String): String {
  return if (isAgentSessionArchivedThreadTitle(title)) title.removePrefix(AGENT_SESSION_ARCHIVED_THREAD_TITLE_PREFIX) else title
}

fun normalizeAgentSessionStoredThreadTitle(value: String?): String? {
  val normalized = value
                     ?.replace('\n', ' ')
                     ?.replace('\r', ' ')
                     ?.replace(Regex("\\s+"), " ")
                     ?.trim()
                   ?: return null
  return normalized.takeIf(String::isNotEmpty)
}
