// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePreCloseCheck
import com.intellij.platform.ai.agent.core.isWorking

internal class AgentChatPreCloseCheck : VirtualFilePreCloseCheck {
  override fun canCloseFile(file: VirtualFile): Boolean {
    return canCloseFiles(listOf(file))
  }

  override fun canCloseFiles(files: Collection<VirtualFile>): Boolean {
    val runningFiles = files.asSequence()
      .filterIsInstance<AgentChatVirtualFile>()
      .filter { it.threadActivity.isWorking }
      .distinctBy { it.tabKey }
      .toList()

    return runningFiles.isEmpty() || showCloseConfirmation(runningFiles)
  }

  private fun showCloseConfirmation(runningFiles: List<AgentChatVirtualFile>): Boolean {
    return Messages.showOkCancelDialog(
      null as Project?,
      confirmationMessage(runningFiles),
      AgentChatBundle.message(if (runningFiles.size == 1) "chat.close.running.session.title" else "chat.close.running.sessions.title"),
      AgentChatBundle.message(if (runningFiles.size == 1) "chat.close.running.session.action.close" else "chat.close.running.sessions.action.close"),
      AgentChatBundle.message("chat.close.running.session.action.keep"),
      Messages.getWarningIcon(),
    ) == Messages.OK
  }

  private fun confirmationMessage(files: List<AgentChatVirtualFile>): @NlsContexts.DialogMessage String {
    return if (files.size == 1) {
      AgentChatBundle.message("chat.close.running.session.message", files.single().threadTitle)
    }
    else {
      AgentChatBundle.message("chat.close.running.sessions.message", files.size)
    }
  }
}
