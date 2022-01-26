// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.SquareStripeButton.Companion.createMoveGroup
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.PopupHandler
import com.intellij.ui.ToggleActionButton
import com.intellij.ui.UIBundle
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import java.util.function.Supplier

internal class SquareStripeButton(val project: Project, val button: StripeButton) :
  ActionButton(SquareAnActionButton(project, button), createPresentation(button), ActionPlaces.TOOLWINDOW_TOOLBAR_BAR, Dimension(40, 40)) {
  companion object {
    @JvmStatic
    fun createMoveGroup(project: Project, _toolWindowsPane: ToolWindowsPane? = null, toolWindow: ToolWindow): DefaultActionGroup {
      var toolWindowsPane = _toolWindowsPane
      if (toolWindowsPane == null) {
        toolWindowsPane = (WindowManager.getInstance() as? WindowManagerImpl)?.getProjectFrameRootPane(project)?.toolWindowPane
        if (toolWindowsPane == null) {
          return DefaultActionGroup()
        }
      }

      val result = DefaultActionGroup.createPopupGroup(Supplier { UIBundle.message("tool.window.new.stripe.move.to.action.group.name") })
      result.add(MoveToAction(toolWindowsPane, toolWindow, ToolWindowAnchor.LEFT))
      result.add(MoveToAction(toolWindowsPane, toolWindow, ToolWindowAnchor.RIGHT))
      result.add(MoveToAction(toolWindowsPane, toolWindow, ToolWindowAnchor.BOTTOM))
      return result
    }
  }

  init {
    setLook(SquareStripeButtonLook(this))
    addMouseListener(object : PopupHandler() {
      override fun invokePopup(component: Component, x: Int, y: Int) {
        showPopup(component, x, y)
      }
    })
    MouseDragHelper.setComponentDraggable(this, true)
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

  fun resetDrop() = resetMouseState()

  fun paintDraggingButton(g: Graphics) {
    val areaSize = size.also {
      JBInsets.removeFrom(it, insets)
      JBInsets.removeFrom(it, SquareStripeButtonLook.ICON_PADDING)
    }

    val rect = Rectangle(areaSize)
    buttonLook.paintLookBackground(g, rect, JBUI.CurrentTheme.ActionButton.pressedBackground())
    icon.let {
      val x = (areaSize.width - it.iconWidth) / 2
      val y = (areaSize.height - it.iconHeight) / 2
      buttonLook.paintIcon(g, this, it, x, y)
    }

    buttonLook.paintLookBorder(g, rect, JBUI.CurrentTheme.ActionButton.pressedBorder())
  }

  override fun updateToolTipText() {
    HelpTooltip()
      .setTitle(button.toolWindow.stripeTitle)
      .setLocation(getAlignment(button.toolWindow.largeStripeAnchor))
      .setShortcut(ActionManager.getInstance().getKeyboardShortcut(ActivateToolWindowAction.getActionIdForToolWindow(button.id)))
      .setInitialDelay(0)
      .setHideDelay(0)
      .installOn(this)
  }

  private fun showPopup(component: Component?, x: Int, y: Int) {
    val popupMenu = ActionManager.getInstance()
      .createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, createPopupGroup(project, button.pane, button.toolWindow))
    popupMenu.component.show(component, x, y)
  }
}

private fun getAlignment(anchor: ToolWindowAnchor): HelpTooltip.Alignment {
  return when (anchor) {
    ToolWindowAnchor.RIGHT -> HelpTooltip.Alignment.LEFT
    ToolWindowAnchor.TOP -> HelpTooltip.Alignment.LEFT
    ToolWindowAnchor.LEFT -> HelpTooltip.Alignment.RIGHT
    ToolWindowAnchor.BOTTOM -> HelpTooltip.Alignment.RIGHT
    else -> HelpTooltip.Alignment.RIGHT
  }
}

private fun createPresentation(button: StripeButton): Presentation {
  val presentation = Presentation(button.text)
  presentation.icon = button.icon ?: AllIcons.Toolbar.Unknown
  presentation.scaleIcon()
  presentation.isEnabledAndVisible = true
  return presentation
}

private fun Presentation.scaleIcon() {
  if (icon is ScalableIcon && icon.iconWidth != 20) {
    icon = IconLoader.loadCustomVersionOrScale(icon as ScalableIcon, 20f)
  }
}

private fun createPopupGroup(project: Project, toolWindowsPane: ToolWindowsPane, toolWindow: ToolWindow): DefaultActionGroup {
  val group = DefaultActionGroup()
  group.add(HideAction(toolWindowsPane, toolWindow))
  group.addSeparator()
  group.add(createMoveGroup(project, toolWindowsPane, toolWindow))
  return group
}

private class MoveToAction(val toolWindowsPane: ToolWindowsPane, val toolWindow: ToolWindow, val anchor: ToolWindowAnchor) :
  AnAction(anchor.capitalizedDisplayName), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    toolWindowsPane.onStripeButtonRemoved(toolWindow)
    toolWindow.isVisibleOnLargeStripe = true
    toolWindow.setLargeStripeAnchor(anchor, if (anchor == ToolWindowAnchor.BOTTOM) 0 else -1)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = toolWindow.largeStripeAnchor != anchor
  }
}

private class HideAction(val toolWindowsPane: ToolWindowsPane, val toolWindow: ToolWindow) : AnAction(
  UIBundle.message("tool.window.new.stripe.hide.action.name")), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    toolWindowsPane.onStripeButtonRemoved(toolWindow)
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