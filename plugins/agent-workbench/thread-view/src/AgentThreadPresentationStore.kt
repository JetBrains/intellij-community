// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// @spec plugins/ij-air/spec/sessions/agent-sessions.spec.md
// @spec plugins/ij-air/spec/thread-view/agent-thread-view.spec.md
package com.intellij.agent.workbench.thread.view

import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadPresentation
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadPresentationKey
import com.intellij.platform.ai.agent.sessions.core.AgentSessionThreadPresentationModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service

internal fun AgentThreadViewVirtualFile.presentationKeyOrNull(): AgentSessionThreadPresentationKey? {
  val provider = provider ?: return null
  return AgentSessionThreadPresentationKey.create(
    projectPath = projectPath,
    provider = provider,
    threadId = sessionId,
  )
}

internal fun resolveAgentThreadViewThreadPresentation(file: AgentThreadViewVirtualFile): AgentSessionThreadPresentation {
  val bootstrapPresentation = AgentSessionThreadPresentation(
    title = file.bootstrapThreadTitle,
    activityReport = file.bootstrapThreadActivityReport,
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
    activityReport = sharedPresentation.activityReport,
    updatedAt = sharedPresentation.updatedAt,
  )
}

internal fun resolveAgentThreadViewConcreteThreadPresentation(
  projectPath: String,
  provider: AgentSessionProvider,
  threadId: String,
  fallbackTitle: String,
  fallbackActivityReport: AgentThreadActivityReport,
): AgentSessionThreadPresentation {
  val fallbackPresentation = AgentSessionThreadPresentation(title = fallbackTitle, activityReport = fallbackActivityReport)
  val key = AgentSessionThreadPresentationKey.create(
    projectPath = projectPath,
    provider = provider,
    threadId = threadId,
  ) ?: return fallbackPresentation
  val application = ApplicationManager.getApplication()
  if (application == null || application.isDisposed) {
    return fallbackPresentation
  }
  val sharedPresentation = application.service<AgentSessionThreadPresentationModel>().resolve(key) ?: return fallbackPresentation
  return AgentSessionThreadPresentation(
    title = sharedPresentation.title.takeIf { it.isNotBlank() } ?: fallbackTitle,
    activityReport = sharedPresentation.activityReport,
    updatedAt = sharedPresentation.updatedAt,
  )
}
