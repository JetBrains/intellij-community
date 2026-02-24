// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction

internal class AgentSessionsArchiveThreadAction : DumbAwareAction {
  private val resolveTreeContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?
  private val resolveEditorContext: (AnActionEvent) -> AgentChatThreadEditorTabActionContext?
  private val canArchiveThread: (AgentSessionThread) -> Boolean
  private val archiveThreads: (List<ArchiveThreadTarget>) -> Unit

  @Suppress("unused")
  constructor() {
    resolveTreeContext = ::resolveAgentSessionsTreePopupActionContext
    resolveEditorContext = ::resolveAgentChatThreadEditorTabActionContext
    canArchiveThread = { thread -> service<AgentSessionsService>().canArchiveThread(thread) }
    archiveThreads = { targets -> service<AgentSessionsService>().archiveThreads(targets) }
  }

  internal constructor(
    resolveTreeContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext?,
    resolveEditorContext: (AnActionEvent) -> AgentChatThreadEditorTabActionContext?,
    canArchiveThread: (AgentSessionThread) -> Boolean,
    archiveThreads: (List<ArchiveThreadTarget>) -> Unit,
  ) {
    this.resolveTreeContext = resolveTreeContext
    this.resolveEditorContext = resolveEditorContext
    this.canArchiveThread = canArchiveThread
    this.archiveThreads = archiveThreads
  }

  override fun update(e: AnActionEvent) {
    val treeContext = resolveTreeContext(e)
    if (treeContext != null) {
      val archiveTargets = treeContext.archiveTargets
      val canArchive = archiveTargets.any { target -> canArchiveThread(target.thread) }
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

    e.presentation.text = templatePresentation.textWithMnemonic
    e.presentation.isVisible = true
    e.presentation.isEnabled = canArchiveThread(editorContext.thread)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val treeContext = resolveTreeContext(e)
    if (treeContext != null) {
      if (treeContext.archiveTargets.none { target -> canArchiveThread(target.thread) }) {
        return
      }
      archiveThreads(treeContext.archiveTargets)
      return
    }

    val editorContext = resolveEditorContext(e) ?: return
    if (!canArchiveThread(editorContext.thread)) {
      return
    }

    archiveThreads(
      listOf(
        ArchiveThreadTarget(
          path = editorContext.path,
          thread = editorContext.thread,
        )
      )
    )
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
