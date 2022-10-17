// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.actions.ToolWindowMoveAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.safeToolWindowPaneId
import com.intellij.ui.UIBundle
import java.util.function.Supplier

internal class ToolWindowMoveToAction(private val _toolWindow: ToolWindow? = null, private val targetAnchor: ToolWindowMoveAction.Anchor) :
  AnAction(targetAnchor.toString()), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    var toolWindow: ToolWindow? = _toolWindow
    if (_toolWindow == null) {
      toolWindow = ToolWindowMoveAction.getToolWindow(e)
    }
    if (toolWindow == null) throw RuntimeException("Cannot move toolwindow")

    val toolWindowManager = ((toolWindow as? ToolWindowImpl)?.toolWindowManager) ?:
    throw RuntimeException("Cannot move toolwindow: " + toolWindow.id)

    val info = toolWindowManager.getLayout().getInfo(toolWindow.id)
    toolWindowManager.setSideToolAndAnchor(id = toolWindow.id,
                                           paneId = info?.safeToolWindowPaneId ?: WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID,
                                           anchor = targetAnchor.anchor,
                                           order = -1,
                                           isSplit = targetAnchor.isSplit)
  }

  override fun update(e: AnActionEvent) {
    var toolWindow: ToolWindow? = _toolWindow
    if (_toolWindow == null) {
      toolWindow = ToolWindowMoveAction.getToolWindow(e)
    }

    e.presentation.isEnabledAndVisible = toolWindow != null && (targetAnchor.anchor != toolWindow.anchor || targetAnchor.isSplit != toolWindow.isSplitMode)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  class Group(val toolWindow: ToolWindow? = null) : DefaultActionGroup(
    Supplier { UIBundle.message("tool.window.move.to.action.group.name") },
    listOf(ToolWindowMoveToAction(toolWindow, ToolWindowMoveAction.Anchor.LeftTop),
           ToolWindowMoveToAction(toolWindow, ToolWindowMoveAction.Anchor.BottomLeft),
           ToolWindowMoveToAction(toolWindow, ToolWindowMoveAction.Anchor.RightTop),
           ToolWindowMoveToAction(toolWindow, ToolWindowMoveAction.Anchor.BottomRight))) {


    override fun isDumbAware() = true
    override fun isPopup() = true
  }
}