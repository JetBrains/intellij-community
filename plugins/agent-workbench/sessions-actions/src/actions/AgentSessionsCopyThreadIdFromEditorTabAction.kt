// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.resolveAgentChatEditorTabActionContext
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

internal class AgentSessionsCopyThreadIdFromEditorTabAction @JvmOverloads constructor(
  private val copyToClipboard: (String) -> Unit = { threadId ->
    CopyPasteManager.getInstance().setContents(StringSelection(threadId))
  },
  resolveContext: (AnActionEvent) -> AgentChatEditorTabActionContext? = ::resolveAgentChatEditorTabActionContext,
) : AgentSessionsEditorTabActionBase(resolveContext) {

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveEditorTabContext(e) ?: return
    val threadCoordinates = resolveAgentSessionsEditorTabThreadCoordinates(context) ?: return
    copyToClipboard(threadCoordinates.threadId)
  }

  override fun update(e: AnActionEvent) {
    val context = resolveEditorTabContextOrHide(e) ?: return

    e.presentation.isVisible = true
    e.presentation.isEnabled = resolveAgentSessionsEditorTabThreadCoordinates(context) != null
  }
}
