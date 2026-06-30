// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.thread.view.AgentThreadViewEditorTabActionContext
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.platform.ai.agent.sessions.core.SessionActionTarget
import com.intellij.platform.ai.agent.sessions.core.formatCompactAgentSessionThreadTitle
import com.intellij.platform.ai.agent.sessions.core.formatCompactAgentSessionTitle
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget

internal fun resolveEditorTabThreadTarget(context: AgentThreadViewEditorTabActionContext): SessionActionTarget.ThreadTarget? {
  return context.sessionActionTarget as? SessionActionTarget.ThreadTarget
}

internal fun resolveArchiveThreadTargetFromEditorTabContext(context: AgentThreadViewEditorTabActionContext): ArchiveThreadTarget? {
  return resolveEditorTabThreadTarget(context)?.toArchiveThreadTarget()
}

internal fun resolvePreferredArchiveNotificationLabelFromEditorTabContext(context: AgentThreadViewEditorTabActionContext): String? {
  val target = resolveEditorTabThreadTarget(context) ?: return null
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

private fun SessionActionTarget.ThreadTarget.toArchiveThreadTarget(): ArchiveThreadTarget {
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
