package com.intellij.toolWindow

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.openapi.wm.impl.ToolWindowImpl

class StripeActionGroup: ActionGroup(), DumbAware {
  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val twm = e?.project?.let { ToolWindowManager.getInstance(it) } ?: return emptyArray()
    val res = ArrayList<AnAction>()
    for (toolWindowId in twm.toolWindowIds) {
      val tw = twm.getToolWindow(toolWindowId) as? ToolWindowImpl ?: continue
      res.add(SquareStripeButton(tw).action)
    }
    return res.toArray(AnAction.EMPTY_ARRAY)
  }
}