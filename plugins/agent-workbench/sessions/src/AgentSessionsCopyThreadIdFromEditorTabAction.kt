// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.resolveAgentChatEditorTabActionContext
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import java.awt.datatransfer.StringSelection

internal class AgentSessionsCopyThreadIdFromEditorTabAction : DumbAwareAction {
  private val resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext?
  private val copyToClipboard: (String) -> Unit

  @Suppress("unused")
  constructor() {
    resolveContext = ::resolveAgentChatEditorTabActionContext
    copyToClipboard = { threadId ->
      CopyPasteManager.getInstance().setContents(StringSelection(threadId))
    }
  }

  internal constructor(
    resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext?,
    copyToClipboard: (String) -> Unit,
  ) {
    this.resolveContext = resolveContext
    this.copyToClipboard = copyToClipboard
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveContext(e) ?: return
    val threadCoordinates = resolveAgentSessionsEditorTabThreadCoordinates(context) ?: return
    copyToClipboard(threadCoordinates.threadId)
  }

  override fun update(e: AnActionEvent) {
    val context = resolveContext(e)
    if (context == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isVisible = true
    e.presentation.isEnabled = resolveAgentSessionsEditorTabThreadCoordinates(context) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
