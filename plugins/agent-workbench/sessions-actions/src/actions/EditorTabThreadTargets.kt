// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.SessionActionTarget
import com.intellij.agent.workbench.sessions.core.formatCompactAgentSessionThreadTitle
import com.intellij.agent.workbench.sessions.core.formatCompactAgentSessionTitle
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget

internal fun resolveEditorTabConversationTarget(context: AgentChatEditorTabActionContext): SessionActionTarget.Conversation? {
  return context.sessionActionTarget as? SessionActionTarget.Conversation
}

internal fun resolveArchiveThreadTargetFromEditorTabContext(context: AgentChatEditorTabActionContext): ArchiveThreadTarget? {
  return resolveEditorTabConversationTarget(context)?.toArchiveThreadTarget()
}

internal fun resolvePreferredArchiveNotificationLabelFromEditorTabContext(context: AgentChatEditorTabActionContext): String? {
  val target = resolveEditorTabConversationTarget(context) ?: return null
  val title = target.title.trim()
  if (title.isEmpty()) {
    return null
  }

  return when (target) {
    is SessionActionTarget.Thread -> {
      formatCompactAgentSessionThreadTitle(
        threadId = target.threadId,
        title = title,
        fallbackTitle = { idPrefix ->
          AgentSessionsBundle.message("toolwindow.thread.fallback.title", idPrefix)
        },
      )
    }

    is SessionActionTarget.SubAgent -> formatCompactAgentSessionTitle(title)
  }
}

private fun SessionActionTarget.Conversation.toArchiveThreadTarget(): ArchiveThreadTarget {
  return when (this) {
    is SessionActionTarget.Thread -> {
      ArchiveThreadTarget.Thread(
        path = path,
        provider = provider,
        threadId = threadId,
      )
    }

    is SessionActionTarget.SubAgent -> {
      ArchiveThreadTarget.SubAgent(
        path = path,
        provider = provider,
        parentThreadId = parentThreadId,
        subAgentId = subAgentId,
      )
    }
  }
}
