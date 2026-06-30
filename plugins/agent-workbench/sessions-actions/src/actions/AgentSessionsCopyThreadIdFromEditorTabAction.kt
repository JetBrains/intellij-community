// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.thread.view.AgentThreadViewEditorTabActionContext
import com.intellij.agent.workbench.thread.view.resolveAgentThreadViewEditorTabActionContext
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

internal class AgentSessionsCopyThreadIdFromEditorTabAction @JvmOverloads constructor(
  private val copyToClipboard: (String) -> Unit = { threadId ->
    CopyPasteManager.getInstance().setContents(StringSelection(threadId))
  },
  resolveContext: (AnActionEvent) -> AgentThreadViewEditorTabActionContext? = ::resolveAgentThreadViewEditorTabActionContext,
) : AgentSessionsEditorTabActionBase(resolveContext) {

  override fun actionPerformed(e: AnActionEvent) {
    val context = resolveEditorTabContext(e) ?: return
    val threadTarget = resolveEditorTabThreadTarget(context) ?: return
    copyToClipboard(threadTarget.threadId)
  }

  override fun update(e: AnActionEvent) {
    val context = resolveEditorTabContextOrHide(e) ?: return

    e.presentation.isVisible = true
    e.presentation.isEnabled = resolveEditorTabThreadTarget(context) != null
  }
}
