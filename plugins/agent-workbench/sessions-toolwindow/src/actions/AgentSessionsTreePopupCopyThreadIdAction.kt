// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.actions

import com.intellij.platform.ai.agent.sessions.core.SessionActionTarget
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import java.awt.datatransfer.StringSelection

internal class AgentSessionsTreePopupCopyThreadIdAction @JvmOverloads constructor(
  private val copyToClipboard: (String) -> Unit = { threadId ->
    CopyPasteManager.getInstance().setContents(StringSelection(threadId))
  },
  private val resolveContext: (AnActionEvent) -> AgentSessionsTreePopupActionContext? = ::resolveAgentSessionsTreePopupActionContext,
) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val target = resolveContext(e)?.target as? SessionActionTarget.Thread
    e.presentation.isEnabledAndVisible = target?.threadId?.isNotBlank() == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val target = resolveContext(e)?.target as? SessionActionTarget.Thread ?: return
    val threadId = target.threadId.takeIf { it.isNotBlank() } ?: return
    copyToClipboard(threadId)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
