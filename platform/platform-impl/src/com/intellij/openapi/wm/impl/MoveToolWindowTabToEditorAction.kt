// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.ui.content.Content

class MoveToolWindowTabToEditorAction : ToolWindowContextMenuActionBase() {
  override fun update(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    e.presentation.isEnabledAndVisible = e.project != null && content?.tabName != null && content.isCloseable
  }

  override fun actionPerformed(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val project = e.project
    if (project == null || content == null) return

    val newFile = ToolWindowTabFileImpl("${content.tabName} (${toolWindow.stripeTitle})", content, toolWindow.icon)
    FileEditorManager.getInstance(project).openFile(newFile, true).first()
  }
}