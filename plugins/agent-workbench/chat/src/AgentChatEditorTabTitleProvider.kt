// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile

private const val MAX_EDITOR_TAB_TITLE_LENGTH = 50

internal class AgentChatEditorTabTitleProvider : EditorTabTitleProvider, DumbAware {
  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    val chatFile = file as? AgentChatVirtualFile ?: return null
    return StringUtil.trimMiddle(chatFile.threadTitle, MAX_EDITOR_TAB_TITLE_LENGTH)
  }

  override fun getEditorTabTooltipText(project: Project, virtualFile: VirtualFile): String? {
    val chatFile = virtualFile as? AgentChatVirtualFile ?: return null
    return chatFile.threadTitle
  }
}
