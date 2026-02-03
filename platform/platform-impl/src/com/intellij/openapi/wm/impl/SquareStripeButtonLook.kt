// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.toolWindow.ResizeStripeManager
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import java.awt.*
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.UIManager

/**
 * @author Konstantin Bulenkov
 */
internal open class SquareStripeButtonLook(private val button: ActionButton) : IdeaActionButtonLook() {
  companion object {
    fun getIconPadding(isLeft: Boolean): Insets {
      return JBUI.CurrentTheme.Toolbar.stripeToolbarButtonIconPadding(
        isLeft,
        ResizeStripeManager.isShowNames()
      )
    }
  }

  override fun paintBackground(g: Graphics, component: JComponent, state: Int) {
    val initialColor = getStateBackground(component, state) ?: return
    val rect = Rectangle(component.size).also {
      JBInsets.removeFrom(it, component.insets)
      JBInsets.removeFrom(it, getIconPadding(component.isOnTheLeftStripe()))
    }

    val color = getBackgroundColor(initialColor)
    paintLookBackground(g, rect, color)
  }

  override fun getState(button: ActionButtonComponent?): Int {
    if (button is SquareStripeButton) {
      if (button.isFocused()) {
        return ActionButtonComponent.SELECTED
      }
      if (button.toolWindow.isVisible) {
        return ActionButtonComponent.PUSHED
      } else if (!button.isHovered()) {
        return ActionButtonComponent.NORMAL
      }
    }
    return super.getState(button)
  }

  override fun paintBorder(g: Graphics, component: JComponent, state: Int) {
    if (button is SquareStripeButton && button.isFocused() ||
        state == ActionButtonComponent.NORMAL && !component.isBackgroundSet) {
      return
    }

    val rect = Rectangle(component.size).also {
      JBInsets.removeFrom(it, component.insets)
      JBInsets.removeFrom(it, getIconPadding(component.isOnTheLeftStripe()))
    }

    val color = if (state == ActionButtonComponent.PUSHED) JBUI.CurrentTheme.ActionButton.pressedBorder()
                  else JBUI.CurrentTheme.ActionButton.hoverBorder()

    paintLookBorder(g, rect, color)
  }

  private fun getBackgroundColor(color: Color): Color {
    if (button is SquareStripeButton) {
      if (button.isFocused()) return UIManager.getColor("ToolWindow.Button.selectedBackground")?: color
    }
    return color
  }

  override fun getIconPosition(actionButton: ActionButtonComponent, icon: Icon): Point {
    val rect = Rectangle(actionButton.getWidth(), actionButton.getHeight())
    JBInsets.removeFrom(rect, actionButton.insets)
    JBInsets.removeFrom(rect, getIconPadding(button.isOnTheLeftStripe()))
    val x = rect.x + (rect.width - icon.iconWidth) / 2
    val y = rect.y + (rect.height - icon.iconHeight) / 2
    return Point(x, y)
  }

  override fun paintIcon(g: Graphics?, actionButton: ActionButtonComponent?, icon: Icon) {
    val color = UIManager.getColor("ToolWindow.Button.selectedForeground")
    if (actionButton !is SquareStripeButton || !actionButton.isFocused() || color == null) {
      super.paintIcon(g, actionButton, icon)
      return
    }

    super.paintIcon(g, actionButton, toStrokeIcon(icon, UIManager.getColor("ToolWindow.Button.selectedForeground")))
  }

  override fun getButtonArc() = JBUI.CurrentTheme.Toolbar.stripeButtonArc(UISettings.getInstance().compactMode)
}
