// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.actions.ToolWindowsGroup
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.toolWindow.ToolWindowEventSource
import com.intellij.toolWindow.xNext.toolbar.data.XNextToolbarManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class XNextToolbarToolWindowsGroup : ActionGroup(), DumbAware {
  private val cache = mutableMapOf<String, AnAction>()

  override fun getChildren(e: AnActionEvent?): Array<out AnAction?> {
    val project = e?.project ?: return emptyArray()
    val list = mutableListOf<AnAction>()

    val toolWindows = ToolWindowsGroup.getToolWindowActions(project, false)

    val state = XNextToolbarManager.getInstance(project).xNextToolbarState

    val pinned = state.pinned
    val recent = state.recent

    list.addAll(toolWindows.filter { pinned.contains(it.toolWindowId) }.sortedBy { pinned.indexOf(it.toolWindowId) }.map { wrap(it) })
    list.add(Separator.create())
    list.addAll(toolWindows.filter { recent.contains(it.toolWindowId) }.sortedByDescending { recent.indexOf(it.toolWindowId) }.map { wrap(it) })

    return list.toTypedArray()
  }

  private fun wrap(action: ActivateToolWindowAction): AnAction {
    return cache.getOrPut(action.toolWindowId) { XNextToolWindowAction(action) }
  }
}

private class XNextToolWindowAction(val action: ActivateToolWindowAction) : AnActionWrapper(action), DumbAware, Toggleable {
  override fun actionPerformed(e: AnActionEvent) {
    val state = !isSelected(e)
    setSelected(e, state)
    Toggleable.setSelected(e.presentation, state)
    super.actionPerformed(e)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    Toggleable.setSelected(e.presentation, isSelected(e))
  }

  private fun isSelected(e: AnActionEvent): Boolean {
    return e.project?.let { ToolWindowManagerEx.getInstanceEx(it) }?.getToolWindow(action.toolWindowId)?.isVisible == true
  }

  private fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    val twm = ToolWindowManager.getInstance(project)
    val toolWindowId = action.toolWindowId
    val toolWindow = twm.getToolWindow(toolWindowId) ?: return
    if (toolWindow.isVisible == state) {
      return
    }
    if (toolWindow.isVisible) {
      if (twm is ToolWindowManagerImpl) {
        twm.hideToolWindow(toolWindowId, false, true, false, ToolWindowEventSource.StripeButton)
      }
      else {
        toolWindow.hide(null)
      }
    }
    else {
      super.actionPerformed(e)
    }
  }
}
