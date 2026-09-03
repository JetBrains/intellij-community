// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import org.jetbrains.annotations.ApiStatus

/**
 * Base class for actions that operate on tool window content shown in an editor tab.
 *
 * To make an action available in the editor tab context menu,
 * register it in the `<group id="EditorTabPopupMenu">` action group.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
abstract class ToolWindowEditorTabActionBase : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  final override fun actionPerformed(e: AnActionEvent) {
    val context = getContext(e) ?: return
    actionPerformed(e, context.content)
  }

  final override fun update(e: AnActionEvent) {
    val context = getContext(e)
    if (context == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    update(e, context.toolWindow, context.content)
  }

  /**
   * Called from [DumbAwareAction.actionPerformed] if the event is associated
   * with a [ToolWindowEditorTabFile].
   *
   * @param e the current action event
   * @param content the content associated with the current editor tab
   */
  abstract fun actionPerformed(
    e: AnActionEvent,
    content: Content,
  )

  /**
   * Called from [DumbAwareAction.update] if the event is associated
   * with a [ToolWindowEditorTabFile].
   *
   * @param e the current action event
   * @param toolWindow the tool window associated with the current editor tab
   * @param content the content associated with the current editor tab
   */
  abstract fun update(
    e: AnActionEvent,
    toolWindow: ToolWindow,
    content: Content,
  )

  private fun getContext(e: AnActionEvent): Context? {
    val project = e.project ?: return null
    val file = e.getData(PlatformDataKeys.FILE_EDITOR)?.file as? ToolWindowEditorTabFile ?: return null
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(file.toolWindowId) ?: return null

    val content = ToolWindowEditorTabManager.getInstance(project).getSession(file)?.content ?: return null

    return Context(
      toolWindow = toolWindow,
      content = content,
    )
  }

  private data class Context(
    val toolWindow: ToolWindow,
    val content: Content,
  )
}
