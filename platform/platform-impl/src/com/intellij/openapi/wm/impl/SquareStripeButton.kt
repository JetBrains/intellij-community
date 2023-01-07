// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.SquareStripeButton.Companion.createMoveGroup
import com.intellij.toolWindow.ToolWindowEventSource
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
import java.awt.event.MouseEvent

internal class SquareStripeButton(val toolWindow: ToolWindowImpl) :
  ActionButton(SquareAnActionButton(toolWindow), createPresentation(toolWindow), ActionPlaces.TOOLWINDOW_TOOLBAR_BAR, Dimension(40, 40)) {
  companion object {
    fun createMoveGroup(toolWindow: ToolWindow) = ToolWindowMoveToAction.Group(toolWindow)
  }

  init {
    setLook(SquareStripeButtonLook(this))
    addMouseListener(object : PopupHandler() {
      override fun invokePopup(component: Component, x: Int, y: Int) {
        val popupMenu = ActionManager.getInstance()
          .createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, createPopupGroup(toolWindow))
        popupMenu.component.show(component, x, y)
      }
    })
    MouseDragHelper.setComponentDraggable(this, true)
  }

  override fun updateUI() {
    super.updateUI()

    myPresentation.icon = toolWindow.icon ?: AllIcons.Toolbar.Unknown
    scaleIcon(myPresentation)
    myPresentation.isEnabledAndVisible = true
  }

  fun updatePresentation() {
    updateToolTipText()

    myPresentation.icon = toolWindow.icon ?: AllIcons.Toolbar.Unknown
    scaleIcon(myPresentation)
  }

  fun isHovered() = myRollover

  fun isFocused() = toolWindow.isActive

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
    @Suppress("DialogTitleCapitalization")
    HelpTooltip()
      .setTitle(toolWindow.stripeTitle)
      .setLocation(getAlignment(toolWindow.anchor, toolWindow.isSplitMode))
      .setShortcut(ActionManager.getInstance().getKeyboardShortcut(ActivateToolWindowAction.getActionIdForToolWindow(toolWindow.id)))
      .setInitialDelay(0)
      .setHideDelay(0)
      .installOn(this)
    HelpTooltip.setMasterPopupOpenCondition(this) { !(parent as AbstractDroppableStripe).isDroppingButton() }
  }

  override fun checkSkipPressForEvent(e: MouseEvent) = e.button != MouseEvent.BUTTON1
}

private fun getAlignment(anchor: ToolWindowAnchor, splitMode: Boolean): HelpTooltip.Alignment {
  return when (anchor) {
    ToolWindowAnchor.RIGHT -> HelpTooltip.Alignment.LEFT
    ToolWindowAnchor.TOP -> HelpTooltip.Alignment.LEFT
    ToolWindowAnchor.LEFT -> HelpTooltip.Alignment.RIGHT
    ToolWindowAnchor.BOTTOM -> if (splitMode) HelpTooltip.Alignment.LEFT else HelpTooltip.Alignment.RIGHT
    else -> HelpTooltip.Alignment.RIGHT
  }
}

private fun createPresentation(toolWindow: ToolWindowImpl): Presentation {
  val presentation = Presentation(toolWindow.stripeTitle)
  presentation.icon = toolWindow.icon ?: AllIcons.Toolbar.Unknown
  scaleIcon(presentation)
  presentation.isEnabledAndVisible = true
  return presentation
}

private fun scaleIcon(presentation: Presentation) {
  if (presentation.icon is ScalableIcon && presentation.icon.iconWidth != 20) {
    presentation.icon = IconLoader.loadCustomVersionOrScale(presentation.icon as ScalableIcon, 20)
  }
}

private fun createPopupGroup(toolWindow: ToolWindowImpl): DefaultActionGroup {
  val group = DefaultActionGroup()
  group.add(HideAction(toolWindow))
  group.addSeparator()
  group.add(createMoveGroup(toolWindow))
  return group
}

private class HideAction(private val toolWindow: ToolWindowImpl)
  : AnAction(UIBundle.message("tool.window.new.stripe.hide.action.name")), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    toolWindow.toolWindowManager.hideToolWindow(id = toolWindow.id,
                                                hideSide = false,
                                                moveFocus = true,
                                                removeFromStripe = true,
                                                source = ToolWindowEventSource.SquareStripeButton)
  }
}

private class SquareAnActionButton(private val window: ToolWindowImpl) : ToggleActionButton(window.stripeTitle, null), DumbAware {
  override fun isSelected(e: AnActionEvent): Boolean {
    e.presentation.icon = window.icon ?: AllIcons.Toolbar.Unknown
    scaleIcon(e.presentation)
    return window.isVisible
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (e.project!!.isDisposed) {
      return
    }

    val manager = window.toolWindowManager
    if (state) {
      manager.activated(window, ToolWindowEventSource.SquareStripeButton)
    }
    else {
      manager.hideToolWindow(id = window.id,
                             hideSide = false,
                             moveFocus = true,
                             removeFromStripe = false,
                             source = ToolWindowEventSource.SquareStripeButton)
    }
  }
}