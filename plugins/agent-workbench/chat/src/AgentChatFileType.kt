// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

internal object AgentChatFileType : FakeFileType() {
  override fun getName(): String = AgentChatBundle.message("chat.filetype.name")

  override fun getDescription(): String = AgentChatBundle.message("chat.filetype.description")

  override fun getIcon(): Icon = AllIcons.Toolwindows.ToolWindowMessages

  override fun isMyFileType(file: VirtualFile): Boolean = file is AgentChatVirtualFile
}
