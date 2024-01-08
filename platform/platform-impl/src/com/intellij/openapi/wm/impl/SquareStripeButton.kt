// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.actions.ToolWindowMoveAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.SquareStripeButton.Companion.createMoveGroup
import com.intellij.toolWindow.ToolWindowEventSource
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.PopupHandler
import com.intellij.ui.UIBundle
import com.intellij.ui.icons.loadIconCustomVersionOrScale
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.util.function.Supplier
import javax.swing.Icon

internal abstract class AbstractSquareStripeButton(
  action: AnAction, presentation: Presentation,
  minimumSize: Supplier<Dimension>? = null
) :
  ActionButton(action, presentation, ActionPlaces.TOOLWINDOW_TOOLBAR_BAR, minimumSize ?: Supplier { JBUI.CurrentTheme.Toolbar.stripeToolbarButtonSize() }) {

  protected fun doInit(popupBuilder: () -> ActionGroup) {
    setLook(SquareStripeButtonLook(this))
    addMouseListener(object : PopupHandler() {
      override fun invokePopup(component: Component, x: Int, y: Int) {
        val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, popupBuilder.invoke())
        popupMenu.component.show(component, x, y)
      }
    })
  }

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
}

internal open class SquareStripeButton(action: SquareAnActionButton, val toolWindow: ToolWindowImpl, presentation: Presentation, minimumSize: Supplier<Dimension>? = null) :
  AbstractSquareStripeButton(action, presentation, minimumSize) {
  constructor(action: SquareAnActionButton, toolWindow: ToolWindowImpl) : this(action, toolWindow, createPresentation(toolWindow))
  constructor(toolWindow: ToolWindowImpl) : this(SquareAnActionButton(toolWindow), toolWindow)
  companion object {
    fun createMoveGroup(): ToolWindowMoveAction.Group = ToolWindowMoveAction.Group()
  }

  init {
    doInit { createPopupGroup(toolWindow) }
    @Suppress("LeakingThis")
    MouseDragHelper.setComponentDraggable(this, true)
  }

  override fun updateUI() {
    super.updateUI()

    myPresentation.icon = scaleIcon((toolWindow.icon ?: AllIcons.Toolbar.Unknown) as ScalableIcon)
    myPresentation.isEnabledAndVisible = true
  }

  fun updatePresentation() {
    updateToolTipText()

    myPresentation.icon = scaleIcon((toolWindow.icon ?: AllIcons.Toolbar.Unknown) as ScalableIcon)
  }

  fun isHovered(): Boolean = myRollover

  open fun isFocused(): Boolean = toolWindow.isActive

  fun resetDrop() {
    resetMouseState()
  }

  override fun updateToolTipText() {
    @Suppress("DialogTitleCapitalization")
    HelpTooltip()
      .setTitle(toolWindow.stripeTitleProvider)
      .setLocation(getAlignment(toolWindow.anchor, toolWindow.isSplitMode))
      .setShortcut(ActionManager.getInstance().getKeyboardShortcut(ActivateToolWindowAction.getActionIdForToolWindow(toolWindow.id)))
      .setInitialDelay(0)
      .setHideDelay(0)
      .installOn(this)
    HelpTooltip.setMasterPopupOpenCondition(this) { !((parent as? AbstractDroppableStripe)?.isDroppingButton() ?: false) }
  }

  override fun checkSkipPressForEvent(e: MouseEvent) = e.button != MouseEvent.BUTTON1

  protected open fun getAlignment(anchor: ToolWindowAnchor, splitMode: Boolean): HelpTooltip.Alignment {
    return when (anchor) {
      ToolWindowAnchor.RIGHT -> HelpTooltip.Alignment.LEFT
      ToolWindowAnchor.TOP -> HelpTooltip.Alignment.LEFT
      ToolWindowAnchor.LEFT -> HelpTooltip.Alignment.RIGHT
      ToolWindowAnchor.BOTTOM -> if (splitMode) HelpTooltip.Alignment.LEFT else HelpTooltip.Alignment.RIGHT
      else -> HelpTooltip.Alignment.RIGHT
    }
  }
}

private fun createPresentation(toolWindow: ToolWindowImpl): Presentation {
  val presentation = Presentation(toolWindow.stripeTitleProvider)
  presentation.iconSupplier = SynchronizedClearableLazy {
    scaleIcon((toolWindow.icon ?: AllIcons.Toolbar.Unknown) as ScalableIcon)
  }
  presentation.isEnabledAndVisible = true
  return presentation
}

private fun scaleIcon(icon: ScalableIcon): Icon {
  val iconSize = JBUI.CurrentTheme.Toolbar.stripeToolbarButtonIconSize()
  return loadIconCustomVersionOrScale(icon = icon, size = iconSize)
}

private fun createPopupGroup(toolWindow: ToolWindowImpl): DefaultActionGroup {
  val group = DefaultActionGroup()
  group.add(HideAction(toolWindow))
  group.addSeparator()
  group.add(createMoveGroup())
  return group
}

private class HideAction(private val toolWindow: ToolWindowImpl)
  : AnAction(UIBundle.messagePointer("tool.window.new.stripe.hide.action.name")), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    toolWindow.toolWindowManager.hideToolWindow(id = toolWindow.id,
                                                hideSide = false,
                                                moveFocus = true,
                                                removeFromStripe = true,
                                                source = ToolWindowEventSource.SquareStripeButton)
  }
}

internal open class SquareAnActionButton(@JvmField protected val window: ToolWindowImpl)
  : DumbAwareToggleAction(window.stripeTitleProvider) {

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean {
    e.presentation.icon = scaleIcon((window.icon ?: AllIcons.Toolbar.Unknown) as ScalableIcon)
    e.presentation.isVisible = window.isShowStripeButton && window.isAvailable
    return window.isVisible
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project
    if (project == null || project.isDisposed) {
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

