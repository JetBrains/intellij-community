// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

@Deprecated("Moved to com.intellij.agent.workbench.chat.AgentChatNewThreadFromEditorTabAction")
internal class AgentSessionsNewSessionFromEditorTabAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    // Action moved to chat module and is no longer wired in plugin XML.
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
