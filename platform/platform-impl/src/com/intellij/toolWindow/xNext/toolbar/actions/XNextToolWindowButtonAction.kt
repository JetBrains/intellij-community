// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.toolWindow.ToolWindowEventSource

internal class XNextToolWindowButtonAction(activateAction: ActivateToolWindowAction) : AnActionWrapper(activateAction), DumbAware, Toggleable, CustomComponentAction {

  private val toolWindowId get() = (delegate as ActivateToolWindowAction).toolWindowId

  override fun actionPerformed(e: AnActionEvent) {
    val state = !isSelected(e)
    setSelected(e, state)
    Toggleable.setSelected(e.presentation, state)
  }

  private fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return

    val twm = ToolWindowManager.Companion.getInstance(project)
    val toolWindowId = toolWindowId
    val toolWindow = twm.getToolWindow(toolWindowId) ?: return

    val visible = toolWindow.isVisible == true
    if (visible == state) {
      return
    }
    if (visible) {
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

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = e.presentation.isEnabled
    Toggleable.setSelected(e.presentation, isSelected(e))
  }

  private fun isSelected(e: AnActionEvent): Boolean {
    return e.project?.let { ToolWindowManagerEx.Companion.getInstanceEx(it) }?.getToolWindow(toolWindowId)?.isVisible == true
  }
}