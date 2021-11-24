// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.WindowManager
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
    setLook(SquareStripeButtonLook(this))
    addMouseListener(object : PopupHandler() {
      override fun invokePopup(component: Component, x: Int, y: Int) {
        showPopup(component, x, y)
      }
    })
  }

  override fun updateUI() {
    super.updateUI()
    myPresentation.apply {
      icon = button.icon ?: AllIcons.Toolbar.Unknown
      scaleIcon()
      isEnabledAndVisible = true
    }
  }

  fun isHovered() = myRollover

  fun isFocused() = button.toolWindow.isActive

  override fun updateToolTipText() {
    HelpTooltip().
      setTitle(button.toolWindow.stripeTitle).
      setLocation(getAlignment(button.toolWindow.largeStripeAnchor)).
      setShortcut(ActionManager.getInstance().getKeyboardShortcut(ActivateToolWindowAction.getActionIdForToolWindow(button.id))).
      setInitialDelay(0).setHideDelay(0).
      installOn(this)
  }

  private fun showPopup(component: Component?, x: Int, y: Int) {
    val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP,
                                                                      createPopupGroup(project, button.pane, button.toolWindow))
    popupMenu.component.show(component, x, y)
  }

  companion object {
    private fun createPresentation(button: StripeButton) =
      Presentation(button.text).apply {
        icon = button.icon ?: AllIcons.Toolbar.Unknown
        scaleIcon()
        isEnabledAndVisible = true
      }

    private fun Presentation.scaleIcon() {
      if (icon is ScalableIcon && icon.iconWidth == 13) icon = (icon as ScalableIcon).scale(20 / 13f)
    }

    private fun createPopupGroup(project: Project, toolWindowsPane: ToolWindowsPane, toolWindow: ToolWindow) = DefaultActionGroup()
      .apply {
        add(HideAction(toolWindowsPane, toolWindow))
        addSeparator()
        add(createMoveGroup(project, toolWindowsPane, toolWindow))
      }

    @JvmStatic
    fun createMoveGroup(project: Project, _toolWindowsPane: ToolWindowsPane? = null, toolWindow: ToolWindow): DefaultActionGroup {
      var toolWindowsPane = _toolWindowsPane
      if (toolWindowsPane == null) {
        toolWindowsPane = (WindowManager.getInstance() as? WindowManagerImpl)?.getProjectFrameRootPane(project)?.toolWindowPane
        if (toolWindowsPane == null) return DefaultActionGroup()
      }

      return DefaultActionGroup.createPopupGroup(Supplier { UIBundle.message("tool.window.new.stripe.move.to.action.group.name") })
        .apply {
          add(MoveToAction(toolWindowsPane, toolWindow, ToolWindowAnchor.LEFT))
          add(MoveToAction(toolWindowsPane, toolWindow, ToolWindowAnchor.RIGHT))
          add(MoveToAction(toolWindowsPane, toolWindow, ToolWindowAnchor.BOTTOM))
        }
    }

    private fun getAlignment(anchor: ToolWindowAnchor) =
      when (anchor) {
        ToolWindowAnchor.RIGHT -> HelpTooltip.Alignment.LEFT
        ToolWindowAnchor.TOP -> HelpTooltip.Alignment.LEFT
        ToolWindowAnchor.LEFT -> HelpTooltip.Alignment.RIGHT
        ToolWindowAnchor.BOTTOM -> HelpTooltip.Alignment.RIGHT
        else -> HelpTooltip.Alignment.RIGHT
      }
  }

  private class MoveToAction(val toolWindowsPane: ToolWindowsPane, val toolWindow: ToolWindow, val anchor: ToolWindowAnchor) :
    AnAction(anchor.capitalizedDisplayName), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
      toolWindowsPane.onStripeButtonRemoved(e.project!!, toolWindow)
      toolWindow.isVisibleOnLargeStripe = true
      toolWindow.largeStripeAnchor = anchor
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = toolWindow.largeStripeAnchor != anchor
    }
  }

  private class HideAction(val toolWindowsPane: ToolWindowsPane, val toolWindow: ToolWindow) :
    AnAction(UIBundle.message("tool.window.new.stripe.hide.action.name")), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
      toolWindowsPane.onStripeButtonRemoved(e.project!!, toolWindow)
      toolWindow.isVisibleOnLargeStripe = false
      (toolWindow as? ToolWindowImpl)?.toolWindowManager?.hideToolWindow(toolWindow.id, false, true, ToolWindowEventSource.SquareStripeButton)
    }
  }

  private class SquareAnActionButton(val project: Project, val button: StripeButton) : ToggleActionButton(button.text, null), DumbAware {
    override fun isSelected(e: AnActionEvent): Boolean {
      e.presentation.icon = button.toolWindow.icon ?: AllIcons.Toolbar.Unknown
      e.presentation.scaleIcon()
      return button.toolWindow.isVisible
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (e.project!!.isDisposed) return

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