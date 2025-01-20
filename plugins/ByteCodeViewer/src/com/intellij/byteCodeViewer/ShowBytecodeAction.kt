// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import com.intellij.ui.content.ContentFactory

internal class ShowBytecodeAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.presentation.setEnabled(event.project != null)
    event.presentation.setIcon(AllIcons.FileTypes.JavaClass)
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val toolWindowManager = ToolWindowManager.getInstance(project)
    val toolWindow = toolWindowManager.getToolWindow(BytecodeToolWindowPanel.TOOL_WINDOW_ID)
                     ?: toolWindowManager.registerToolWindow(BytecodeToolWindowPanel.TOOL_WINDOW_ID) {
                       icon = AllIcons.FileTypes.JavaClass
                       anchor = ToolWindowAnchor.RIGHT
                       hideOnEmptyContent = true
                       canCloseContent = true
                     }
    val editor = event.getData<Editor>(CommonDataKeys.EDITOR) ?: return
    val psiFile = event.getData<PsiFile>(CommonDataKeys.PSI_FILE) ?: return
    val psiElement = psiFile.findElementAt(editor.caretModel.offset) ?: return
    val psiClass = ByteCodeViewerManager.getContainingClass(psiElement) ?: return
    val clsFile = ByteCodeViewerManager.findClassFile(psiClass) ?: return
    val panel = BytecodeToolWindowPanel(project, psiClass, clsFile)
    val content = toolWindow.contentManager.contents.firstOrNull { it.description == clsFile.presentableUrl }
                  ?: ContentFactory.getInstance().createContent(panel, clsFile.presentableName, false).apply {
                    description = clsFile.presentableUrl
                  }
    toolWindow.contentManager.addContent(content)
    content.setDisposer(panel)
    toolWindow.contentManager.setSelectedContent(content)
    toolWindow.setAdditionalGearActions(createActionGroup())
    toolWindow.activate(null)
  }

  private fun createActionGroup(): ActionGroup {
    val action = object : ToggleAction(BytecodeViewerBundle.messagePointer("action.show.debug.action.name")) {
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

      override fun isSelected(e: AnActionEvent): Boolean {
        return BytecodeViewerSettings.getInstance().state.showDebugInfo
      }

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        BytecodeViewerSettings.getInstance().state.showDebugInfo = state
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(BytecodeToolWindowPanel.TOOL_WINDOW_ID) ?: return
        toolWindow.contentManager.contents.forEach {
          val panel = it.component as? BytecodeToolWindowPanel ?: return@forEach
          panel.setEditorText()
        }
      }
    }
    return DefaultActionGroup(action)
  }
}
