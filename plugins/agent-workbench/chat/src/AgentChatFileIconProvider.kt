// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.chat.icons.AgentWorkbenchChatIcons
import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

internal class AgentChatFileIconProvider : FileIconProvider {
  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    val chatFile = file as? AgentChatVirtualFile ?: return null
    return providerIcon(threadIdentity = chatFile.threadIdentity)
  }
}

internal fun providerIcon(threadIdentity: String): Icon {
  val providerId = threadIdentity.substringBefore(':').lowercase()
  return when (providerId) {
    "codex" -> AgentWorkbenchChatIcons.Codex_14x14
    "claude" -> AgentWorkbenchChatIcons.Claude_14x14
    else -> AllIcons.Toolwindows.ToolWindowMessages
  }
}
