// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.actions.ToolWindowMoveAction
import com.intellij.ide.actions.ToolWindowsGroup
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.AbstractSquareStripeButton
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.toolWindow.ToolWindowDragHelper
import com.intellij.toolWindow.ToolWindowEventSource
import com.intellij.toolWindow.xNext.toolbar.data.XNextToolbarManager
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.PopupHandler
import com.intellij.ui.UIBundle
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
internal class XNextToolbarToolWindowsGroup : ActionGroup(), DumbAware {
  private val cache = mutableMapOf<String, AnAction>()

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
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

private class XNextToolWindowAction(val toolWindowAction: ActivateToolWindowAction) : AnActionWrapper(toolWindowAction),
                                                                                      DumbAware, Toggleable,
                                                                                      CustomComponentAction {
  companion object {
    private val toolWindowKey = Key<ToolWindowImpl>("XNextToolWindowAction.toolWindowKey")
  }

  override fun actionPerformed(e: AnActionEvent) {
    val state = !isSelected(e)
    setSelected(e, state)
    Toggleable.setSelected(e.presentation, state)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    Toggleable.setSelected(e.presentation, isSelected(e))
    val project = e.project ?: return
    val twm = ToolWindowManager.getInstance(project)
    val toolWindowId = toolWindowAction.toolWindowId
    val toolWindow = twm.getToolWindow(toolWindowId) as? ToolWindowImpl ?: return
    e.presentation.putClientProperty(toolWindowKey, toolWindow)
  }

  private fun isSelected(e: AnActionEvent): Boolean {
    return e.project?.let { ToolWindowManagerEx.getInstanceEx(it) }?.getToolWindow(toolWindowAction.toolWindowId)?.isVisible == true
  }

  private fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    val twm = ToolWindowManager.getInstance(project)
    val toolWindowId = toolWindowAction.toolWindowId
    val toolWindow = twm.getToolWindow(toolWindowId) ?: run {
      super.actionPerformed(e)
      return
    }

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

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return MyButton(this, presentation, place)
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    if (component !is MyButton) return
    component.myToolWindow = presentation.getClientProperty(toolWindowKey)
  }

  private class MyButton(action: XNextToolWindowAction, presentation: Presentation, place: String) :
    AbstractSquareStripeButton(action, presentation, { ActionToolbar.experimentalToolbarMinimumButtonSize() }), ToolWindowDragHelper.ToolWindowProvider, UiDataProvider {

      var myToolWindow = presentation.getClientProperty(toolWindowKey)

    override val toolWindow: ToolWindowImpl?
      get() = myToolWindow

    init {
      PopupHandler.installPopupMenu(this,
                                    DefaultActionGroup(
                                      MyPinAction(action.toolWindowAction.toolWindowId),
                                      ToolWindowMoveAction.Group()), "XNextStatusBar.Popup")
      MouseDragHelper.setComponentDraggable(this, true)
    }

    override fun uiDataSnapshot(sink: DataSink) {
      sink[PlatformDataKeys.TOOL_WINDOW] = myToolWindow
    }
  }

  private class MyPinAction(val toolWindowId: String) : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      val project = e.project ?: return
      val mng = XNextToolbarManager.getInstance(project)
      e.presentation.text = if (mng.isPinned(toolWindowId)) UIBundle.message("xnext.toolbar.context.unpin") else UIBundle.message("xnext.toolbar.context.pin")
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      val mng = XNextToolbarManager.getInstance(project)
      mng.setPinned(toolWindowId, mng.isPinned(toolWindowId).not())
      update(e)
    }

  }
}

