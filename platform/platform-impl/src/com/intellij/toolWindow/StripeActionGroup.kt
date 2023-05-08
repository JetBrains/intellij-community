package com.intellij.toolWindow

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.util.containers.ContainerUtil

class StripeActionGroup: ActionGroup(), DumbAware {
  private val myFactory: Map<ToolWindowImpl, AnAction> = ConcurrentFactoryMap.create(::createAction) {
    ContainerUtil.createConcurrentWeakKeyWeakValueMap()
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val twm = e?.project?.let { ToolWindowManager.getInstance(it) } ?: return emptyArray()
    val toolWindows = twm.toolWindowIds.mapNotNullTo(ArrayList()) { twm.getToolWindow(it) as? ToolWindowImpl }
    toolWindows.sortBy(::getOrder)
    return toolWindows.mapNotNull(myFactory::get).toTypedArray()
  }

  private fun getOrder(tw: ToolWindowImpl): Int =
    tw.windowInfo.run {
      when (anchor) {
        ToolWindowAnchor.LEFT -> 0 + order
        ToolWindowAnchor.TOP -> 100 + order
        ToolWindowAnchor.BOTTOM -> 200 + order
        ToolWindowAnchor.RIGHT -> 300 + order
        else -> -1
      }
    }

  private fun createAction(tw: ToolWindowImpl) = SquareStripeButton(tw).action
}