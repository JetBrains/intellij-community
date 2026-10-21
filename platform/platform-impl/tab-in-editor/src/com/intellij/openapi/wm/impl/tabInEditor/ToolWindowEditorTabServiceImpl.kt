// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.impl.InternalDecorator
import com.intellij.openapi.wm.impl.ToolWindowEditorTabService
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.content.Content

internal class ToolWindowEditorTabServiceImpl : ToolWindowEditorTabService {
  override fun isEnabled(): Boolean = ToolWindowEditorTabSupportUtil.isEnabled()

  override fun hasSupport(toolWindowId: String): Boolean = ToolWindowEditorTabSupportUtil.hasSupport(toolWindowId)

  override fun installDockContainer(project: Project, toolWindowId: String, decorator: InternalDecorator) {
    ToolWindowEditorTabDockContainer.install(project, toolWindowId, decorator)
  }

  override fun canMoveContentToEditor(toolWindow: ToolWindow, content: Content): Boolean {
    return ToolWindowEditorTabTransferController.getInstance(toolWindow.project).canMoveContentToEditor(toolWindow, content)
  }

  override fun moveContentToEditor(
    toolWindow: ToolWindow,
    content: Content,
    window: EditorWindow,
    sourceDecorator: InternalDecoratorImpl,
  ): Boolean {
    val controller = ToolWindowEditorTabTransferController.getInstance(toolWindow.project)
    return controller.moveContentToEditor(toolWindow, content, window, sourceDecorator)
  }
}
