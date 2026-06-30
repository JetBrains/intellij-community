// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.SessionActionTarget
import com.intellij.platform.ai.agent.sessions.core.isAgentSessionPendingThreadId
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project

data class AgentThreadViewEditorTabActionContext(
  @JvmField val project: Project,
  @JvmField val path: String,
  @JvmField val tabKey: String,
  @JvmField val threadIdentity: String = "",
  @JvmField val threadActivity: AgentThreadActivity = AgentThreadActivity.READY,
  val threadCoordinates: AgentThreadViewThreadCoordinates? = null,
  val sessionActionTarget: SessionActionTarget? = null,
  @JvmField val projectDirectory: String? = null,
)

data class AgentThreadViewThreadCoordinates(
  val provider: AgentSessionProvider,
  @JvmField val sessionId: String,
  @JvmField val isPending: Boolean,
  @JvmField val participatesInPendingThreadLifecycle: Boolean = isPending,
)

internal fun resolveAgentThreadViewThreadCoordinates(threadIdentity: String): AgentThreadViewThreadCoordinates? {
  val separator = threadIdentity.indexOf(':')
  if (separator <= 0 || separator == threadIdentity.lastIndex) {
    return null
  }

  val providerId = threadIdentity.substring(0, separator).lowercase()
  val provider = AgentSessionProvider.fromOrNull(providerId) ?: return null
  val sessionId = threadIdentity.substring(separator + 1)
  if (sessionId.isBlank()) {
    return null
  }

  return AgentThreadViewThreadCoordinates(
    provider = provider,
    sessionId = sessionId,
    isPending = isAgentSessionPendingThreadId(sessionId),
  )
}

fun resolveAgentThreadViewEditorTabActionContext(event: AnActionEvent): AgentThreadViewEditorTabActionContext? {
  val project = event.project ?: return null
  val selectedThreadViewFile = event.getData(CommonDataKeys.VIRTUAL_FILE) as? AgentThreadViewVirtualFile ?: return null
  val threadCoordinates = selectedThreadViewFile.provider
    ?.let { provider ->
      AgentThreadViewThreadCoordinates(
        provider = provider,
        sessionId = selectedThreadViewFile.sessionId,
        isPending = selectedThreadViewFile.isPendingThread,
        participatesInPendingThreadLifecycle = selectedThreadViewFile.participatesInPendingThreadLifecycle(),
      )
    }
  return AgentThreadViewEditorTabActionContext(
    project = project,
    path = normalizeAgentWorkbenchPath(selectedThreadViewFile.projectPath),
    tabKey = selectedThreadViewFile.tabKey,
    threadIdentity = selectedThreadViewFile.threadIdentity,
    threadActivity = selectedThreadViewFile.threadActivity,
    threadCoordinates = threadCoordinates,
    projectDirectory = selectedThreadViewFile.projectDirectory,
    sessionActionTarget = resolveAgentThreadViewSessionActionTarget(
      path = normalizeAgentWorkbenchPath(selectedThreadViewFile.projectPath),
      threadCoordinates = threadCoordinates,
      threadId = selectedThreadViewFile.threadId,
      threadTitle = selectedThreadViewFile.threadTitle,
      subAgentId = selectedThreadViewFile.subAgentId,
    ),
  )
}

private fun resolveAgentThreadViewSessionActionTarget(
  path: String,
  threadCoordinates: AgentThreadViewThreadCoordinates?,
  threadId: String,
  threadTitle: String,
  subAgentId: String?,
): SessionActionTarget? {
  val coordinates = threadCoordinates ?: return null
  if (coordinates.isPending) {
    return null
  }

  val normalizedSubAgentId = subAgentId?.takeIf { it.isNotBlank() }
  val resolvedThreadId = threadId.takeIf { it.isNotBlank() } ?: normalizedSubAgentId ?: coordinates.sessionId
  if (resolvedThreadId.isBlank()) {
    return null
  }

  return if (normalizedSubAgentId == null) {
    SessionActionTarget.Thread(
      path = path,
      provider = coordinates.provider,
      threadId = resolvedThreadId,
      title = threadTitle,
    )
  }
  else {
    if (resolvedThreadId != normalizedSubAgentId) {
      return null
    }
    SessionActionTarget.SubAgent(
      path = path,
      provider = coordinates.provider,
      parentThreadId = coordinates.sessionId,
      subAgentId = normalizedSubAgentId,
      title = threadTitle,
    )
  }
}
