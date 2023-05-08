package com.intellij.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.ui.ToggleActionButton
import javax.swing.JComponent

class StripeActionGroup: ActionGroup(), DumbAware {
  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val twm = e?.project?.let { ToolWindowManager.getInstance(it) } ?: return emptyArray()
    val res = ArrayList<AnAction>()

    val toolWindows = twm.toolWindowIds.mapNotNullTo(ArrayList()) { twm.getToolWindow(it) as? ToolWindowImpl }
    toolWindows.sortBy { tw -> tw.windowInfo.run { when (anchor) {
      ToolWindowAnchor.LEFT -> 0 + order
      ToolWindowAnchor.TOP -> 100 + order
      ToolWindowAnchor.BOTTOM -> 200 + order
      ToolWindowAnchor.RIGHT -> 300 + order
      else -> -1
    } } }
    for (tw in toolWindows) {
      res.add(SquareStripeButton(tw).action)
    }
    return res.toArray(AnAction.EMPTY_ARRAY)
  }
}