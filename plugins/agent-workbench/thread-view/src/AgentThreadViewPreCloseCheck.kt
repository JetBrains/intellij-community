// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.thread.view

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePreCloseCheck
import com.intellij.platform.ai.agent.core.isWorking

internal class AgentThreadViewPreCloseCheck : VirtualFilePreCloseCheck {
  override fun canCloseFile(file: VirtualFile): Boolean {
    return canCloseFiles(listOf(file))
  }

  override fun canCloseFiles(files: Collection<VirtualFile>): Boolean {
    val runningFiles = files.asSequence()
      .filterIsInstance<AgentThreadViewVirtualFile>()
      .filter { it.threadActivity.isWorking }
      .distinctBy { it.tabKey }
      .toList()

    return runningFiles.isEmpty() || showCloseConfirmation(runningFiles)
  }

  private fun showCloseConfirmation(runningFiles: List<AgentThreadViewVirtualFile>): Boolean {
    return Messages.showOkCancelDialog(
      null as Project?,
      confirmationMessage(runningFiles),
      AgentThreadViewBundle.message(if (runningFiles.size == 1) "thread.view.close.running.session.title" else "thread.view.close.running.sessions.title"),
      AgentThreadViewBundle.message(if (runningFiles.size == 1) "thread.view.close.running.session.action.close" else "thread.view.close.running.sessions.action.close"),
      AgentThreadViewBundle.message("thread.view.close.running.session.action.keep"),
      Messages.getWarningIcon(),
    ) == Messages.OK
  }

  private fun confirmationMessage(files: List<AgentThreadViewVirtualFile>): @NlsContexts.DialogMessage String {
    return if (files.size == 1) {
      AgentThreadViewBundle.message("thread.view.close.running.session.message", files.single().threadTitle)
    }
    else {
      AgentThreadViewBundle.message("thread.view.close.running.sessions.message", files.size)
    }
  }
}
