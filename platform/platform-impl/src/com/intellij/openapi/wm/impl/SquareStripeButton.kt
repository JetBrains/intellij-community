// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.ToggleActionButton
import com.intellij.ui.UIBundle
import java.awt.Component
import java.awt.Dimension
import java.util.function.Supplier

class SquareStripeButton(val project: Project, val button: StripeButton) :
  ActionButton(SquareAnActionButton(project, button), createPresentation(button), ActionPlaces.TOOLWINDOW_TOOLBAR_BAR, Dimension(40, 40)) {

  init {
    addMouseListener(object : PopupHandler() {
      override fun invokePopup(component: Component, x: Int, y: Int) {
        showPopup(component, x, y)
      }
    })
  }

  private fun showPopup(component: Component?, x: Int, y: Int) {
    val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, createPopupGroup())
    popupMenu.component.show(component, x, y)
  }

  private fun createPopupGroup() = DefaultActionGroup()
    .apply {
      add(HideAction())
      addSeparator()
      add(DefaultActionGroup.createPopupGroup(Supplier { UIBundle.message("tool.window.new.stripe.move.to.action.group.name") })
            .apply {
              add(MoveToAction(ToolWindowAnchor.LEFT))
              add(MoveToAction(ToolWindowAnchor.RIGHT))
              add(MoveToAction(ToolWindowAnchor.BOTTOM))
            })
    }

  companion object {
    private fun createPresentation(button: StripeButton) =
      Presentation(button.text).apply {
        icon = button.icon
        if (icon is ScalableIcon) icon = (icon as ScalableIcon).scale(1.4f)
        isEnabledAndVisible = true
      }
  }

  private inner class MoveToAction(val anchor: ToolWindowAnchor) : AnAction(anchor.capitalizedDisplayName), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
      button.pane.onStripeButtonRemoved(project, button.toolWindow)
      button.toolWindow.isVisibleOnLargeStripe = true
      button.pane.onStripeButtonAdded(project, button.toolWindow, anchor, Comparator { _, _ -> 0 })
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = button.toolWindow.largeStripeAnchor != anchor
    }
  }

  private inner class HideAction : AnAction(UIBundle.message("tool.window.new.stripe.hide.action.name")), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
      button.pane.onStripeButtonRemoved(project, button.toolWindow)
      button.toolWindow.toolWindowManager.hideToolWindow(button.id, false, true, ToolWindowEventSource.SquareStripeButton)
    }
  }

  private class SquareAnActionButton(val project: Project, val button: StripeButton) : ToggleActionButton(button.text, null), DumbAware {
    override fun isSelected(e: AnActionEvent?) = button.toolWindow.isVisible
    override fun setSelected(e: AnActionEvent?, state: Boolean) {
      val manager = button.toolWindow.toolWindowManager
      if (!state) {
        manager.hideToolWindow(button.id, false, true, ToolWindowEventSource.SquareStripeButton)
      }
      else {
        manager.activated(button.toolWindow, ToolWindowEventSource.SquareStripeButton)
      }
      project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).stateChanged(manager)
    }
  }
}