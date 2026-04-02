// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.sessions.core.formatCompactAgentSessionTitle
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VirtualFile

internal class AgentChatEditorTabTitleProvider : EditorTabTitleProvider, DumbAware {
  override suspend fun getEditorTabTitleAsync(project: Project, file: VirtualFile): @NlsContexts.TabTitle String? {
    return getEditorTabTitle(project, file)
  }

  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    val chatFile = file as? AgentChatVirtualFile ?: return null
    return formatCompactAgentSessionTitle(chatFile.threadTitle)
  }

  override fun getEditorTabTooltipHtml(project: Project, virtualFile: VirtualFile): HtmlChunk? {
    val chatFile = virtualFile as? AgentChatVirtualFile ?: return null
    return HtmlChunk.text(chatFile.threadTitle)
  }
}
