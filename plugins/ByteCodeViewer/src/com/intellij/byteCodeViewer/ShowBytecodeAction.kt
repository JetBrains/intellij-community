// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import com.intellij.ui.content.ContentFactory

internal class ShowBytecodeAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    val file = event.getData<PsiFile>(CommonDataKeys.PSI_FILE) ?: return
    val fileType = file.getFileType()
    event.presentation.setEnabled(event.project != null && isValidFileType(fileType))
    event.presentation.setIcon(AllIcons.FileTypes.JavaClass)
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    event.getData<VirtualFile>(CommonDataKeys.VIRTUAL_FILE) ?: return
    val editor = event.getData<Editor>(CommonDataKeys.EDITOR) ?: return
    val toolWindowManager = ToolWindowManager.getInstance(project)
    val toolWindow = toolWindowManager.getToolWindow(BytecodeToolWindowPanel.TOOL_WINDOW_ID)
                     ?: toolWindowManager.registerToolWindow(BytecodeToolWindowPanel.TOOL_WINDOW_ID) {
                       icon = AllIcons.FileTypes.JavaClass
                       anchor = ToolWindowAnchor.RIGHT
                       hideOnEmptyContent = false
                       canCloseContent = false
                     }
    val panel = BytecodeToolWindowPanel(project, toolWindow, editor)
    toolWindow.getContentManager().addContent( ContentFactory.getInstance().createContent(panel, "", false))
    toolWindow.activate(null)
  }
}
