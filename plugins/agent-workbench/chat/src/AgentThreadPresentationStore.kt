// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// @spec community/plugins/agent-workbench/spec/agent-sessions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentation
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationKey
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service

internal fun AgentChatVirtualFile.presentationKeyOrNull(): AgentSessionThreadPresentationKey? {
  val provider = provider ?: return null
  return AgentSessionThreadPresentationKey.create(
    projectPath = projectPath,
    provider = provider,
    threadId = sessionId,
  )
}

internal fun resolveAgentChatThreadPresentation(file: AgentChatVirtualFile): AgentSessionThreadPresentation {
  val bootstrapPresentation = AgentSessionThreadPresentation(
    title = file.bootstrapThreadTitle,
    activity = file.bootstrapThreadActivity,
  )
  if (file.isPendingThread) {
    return bootstrapPresentation
  }

  val key = file.presentationKeyOrNull() ?: return bootstrapPresentation
  val application = ApplicationManager.getApplication()
  if (application == null || application.isDisposed) {
    return bootstrapPresentation
  }
  val sharedPresentation = application.service<AgentSessionThreadPresentationModel>().resolve(key) ?: return bootstrapPresentation

  val resolvedTitle = if (file.subAgentId == null) {
    sharedPresentation.title.takeIf { it.isNotBlank() } ?: bootstrapPresentation.title
  }
  else {
    bootstrapPresentation.title
  }
  return AgentSessionThreadPresentation(
    title = resolvedTitle,
    activity = sharedPresentation.activity,
  )
}
