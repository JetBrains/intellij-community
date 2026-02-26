// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.resolveAgentChatEditorTabActionContext
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction

internal class AgentSessionsArchiveThreadAction : DumbAwareAction {
  private val resolveTreeContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?
  private val resolveEditorContext: (AnActionEvent) -> AgentChatEditorTabActionContext?
  private val canArchiveProvider: (AgentSessionProvider) -> Boolean
  private val archiveThreads: (List<ArchiveThreadTarget>) -> Unit

  @Suppress("unused")
  constructor() {
    resolveTreeContext = ::resolveAgentSessionsTreePopupActionContext
    resolveEditorContext = ::resolveAgentChatEditorTabActionContext
    canArchiveProvider = { provider -> service<AgentSessionsService>().canArchiveProvider(provider) }
    archiveThreads = { targets -> service<AgentSessionsService>().archiveThreads(targets) }
  }

  internal constructor(
    resolveTreeContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?,
    resolveEditorContext: (AnActionEvent) -> AgentChatEditorTabActionContext?,
    canArchiveProvider: (AgentSessionProvider) -> Boolean,
    archiveThreads: (List<ArchiveThreadTarget>) -> Unit,
  ) {
    this.resolveTreeContext = resolveTreeContext
    this.resolveEditorContext = resolveEditorContext
    this.canArchiveProvider = canArchiveProvider
    this.archiveThreads = archiveThreads
  }

  override fun update(e: AnActionEvent) {
    val treeContext = resolveTreeContext(e)
    if (treeContext != null) {
      val archiveTargets = treeContext.archiveTargets
      val canArchive = archiveTargets.any { target -> canArchiveProvider(target.provider) }
      e.presentation.isEnabledAndVisible = canArchive
      if (canArchive) {
        e.presentation.text = if (archiveTargets.size > 1) {
          AgentSessionsBundle.message("toolwindow.action.archive.selected.count", archiveTargets.size)
        }
        else {
          AgentSessionsBundle.message("toolwindow.action.archive")
        }
      }
      return
    }

    val editorContext = resolveEditorContext(e)
    if (editorContext == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val archiveTarget = archiveTargetFromEditorContext(editorContext)
    e.presentation.text = templatePresentation.textWithMnemonic
    e.presentation.isVisible = true
    e.presentation.isEnabled = archiveTarget != null && canArchiveProvider(archiveTarget.provider)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val treeContext = resolveTreeContext(e)
    if (treeContext != null) {
      if (treeContext.archiveTargets.none { target -> canArchiveProvider(target.provider) }) {
        return
      }
      archiveThreads(treeContext.archiveTargets)
      return
    }

    val editorContext = resolveEditorContext(e) ?: return
    val archiveTarget = archiveTargetFromEditorContext(editorContext) ?: return
    if (!canArchiveProvider(archiveTarget.provider)) {
      return
    }
    archiveThreads(listOf(archiveTarget))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private fun archiveTargetFromEditorContext(context: AgentChatEditorTabActionContext): ArchiveThreadTarget? {
  if (context.isPendingThread) {
    return null
  }
  val provider = context.provider ?: return null
  val threadId = context.sessionId.takeIf { it.isNotBlank() } ?: return null
  return ArchiveThreadTarget(path = context.path, provider = provider, threadId = threadId)
}
