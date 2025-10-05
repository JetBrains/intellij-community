// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.actions.ToolWindowMoveAction
import com.intellij.ide.actions.ToolWindowsGroup
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.toolWindow.xNext.toolbar.data.XNextToolbarManager
import com.intellij.ui.UIBundle

internal class XNextToolWindowsMoreGroup : ActionGroup(), DumbAware {
  init {
    isPopup = true
    templatePresentation.icon = AllIcons.Toolwindows.ToolWindowComponents
    templatePresentation.text = UIBundle.message("more.button.accessible.name")
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.project ?: return emptyArray()
    val toolWindowActions = ToolWindowsGroup.getToolWindowActions(project, false)

    val list = mutableListOf<AnAction>()
    val anchorMap = toolWindowActions.groupBy {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(it.toolWindowId)
      if (toolWindow == null) {
        return@groupBy null
      }

      ToolWindowMoveAction.Anchor.getAnchor(toolWindow.anchor, toolWindow.isSplitMode)
    }

    anchorMap.forEach {
      it.key?.let { anchor ->
        list.add(Separator.create(anchor.toString()))
        it.value.sortedBy { tw ->
          val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(tw.toolWindowId)
          (toolWindow as? ToolWindowImpl)?.windowInfo?.order
        }.forEach { tw -> list.add(wrap(tw)) }
      }
    }

    anchorMap[null]?.let { value ->
      list.add(Separator.create())
      value.forEach {
        list.add(wrap(it))
      }
    }

    return list.toTypedArray()
  }


  private fun wrap(action: ActivateToolWindowAction): AnAction {
    return object : AnActionWrapper(action) {
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
      override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isVisible = e.presentation.isVisible && e.presentation.isEnabled
        e.presentation.putClientProperty(ActionUtil.INLINE_ACTIONS, listOf(PinAction(action.toolWindowId)))
      }
    }
  }
}

internal class PinAction(val toolWindowId: String):  DumbAwareToggleAction(){

  init {
    templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.IfPreferred
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    val mng = XNextToolbarManager.getInstance(project)
    return mng.isPinned(toolWindowId)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    val mng = XNextToolbarManager.getInstance(project)
    mng.setPinned(toolWindowId, state)
  }

  override fun update(e: AnActionEvent) {
    val pinned = isSelected(e)

    e.presentation.description = if (pinned)
      UIBundle.message("xnext.action.unpin.tab.tooltip")
    else
      UIBundle.message("xnext.action.pin.tab.tooltip")

    e.presentation.icon = if (!pinned) null else AllIcons.General.PinSelected
    e.presentation.selectedIcon = if (!pinned) null else AllIcons.General.PinSelectedHovered
    e.presentation.putClientProperty(ActionUtil.ALWAYS_VISIBLE_INLINE_ACTION, pinned)
  }
}
