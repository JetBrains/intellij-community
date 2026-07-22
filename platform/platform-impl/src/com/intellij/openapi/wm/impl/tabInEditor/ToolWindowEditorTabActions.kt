// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.toolWindow.InternalDecoratorImpl

internal class MoveToolWindowTabToEditorAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)
    val content = ToolWindowContextMenuActionBase.getContextContent(e)
    val enabled = ToolWindowEditorTabSupportUtil.isEnabled() &&
                  project != null &&
                  toolWindow != null &&
                  content != null &&
                  ToolWindowEditorTabTransferController.getInstance(project).canMoveContentToEditor(toolWindow)

    e.presentation.isEnabledAndVisible = enabled
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW) ?: return
    val content = ToolWindowContextMenuActionBase.getContextContent(e) ?: return

    val sourceDecorator = content.manager
      ?.component
      ?.let(InternalDecoratorImpl::findNearestDecorator)
      ?: ToolWindowContextMenuActionBase.findNearestDecorator(e)

    ToolWindowEditorTabTransferController.getInstance(project).moveContentToEditor(toolWindow, content, e.getData(EditorWindow.DATA_KEY), sourceDecorator)

    if (toolWindow.contentManager.contentsRecursively.isEmpty()) {
      toolWindow.hide()
    }
  }
}

internal class MoveToolWindowTabFromEditorToToolWindowAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val context = getContext(e)
    val enabled = ToolWindowEditorTabSupportUtil.isEnabled() && context != null

    e.presentation.isEnabledAndVisible = enabled
    if (!enabled) return
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = getContext(e) ?: return
    ToolWindowEditorTabTransferController.getInstance(context.project).moveContentToToolWindow(context.toolWindow, context.file)
  }

  private fun getContext(e: AnActionEvent): Context? {
    val project = e.project ?: return null
    val file = e.getData(PlatformDataKeys.FILE_EDITOR)?.file as? ToolWindowEditorTabFile ?: return null
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(file.toolWindowId) ?: return null
    if (!ToolWindowEditorTabTransferController.getInstance(project).canMoveContentToToolWindow(toolWindow, file)) {
      return null
    }

    return Context(project, toolWindow, file)
  }

  private data class Context(
    val project: Project,
    val toolWindow: ToolWindow,
    val file: ToolWindowEditorTabFile,
  )
}
