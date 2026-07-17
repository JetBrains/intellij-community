// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.toolWindow.ResizeStripeManager
import com.intellij.ui.icons.HoledIcon
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.UIManager

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
open class SquareStripeButtonLook(private val button: AbstractSquareStripeButton) : IdeaActionButtonLook() {
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
    if (icon is HoledIcon) {
      // If the icon has a badge, we need to make sure that the original icon stays in place and not "dancing"
      // as the badge is added and removed (e.g., a build is starting and finishing).
      val originalIcon = icon.icon
      val point = centerIcon(rect, originalIcon)
      // Now we have exactly the same location as the original icon,
      // but we need to shift it to take the badge into consideration.
      val badgeInsets = icon.getExtraInsets()
      point.x -= badgeInsets.left
      point.y -= badgeInsets.top
      return point
    }
    else {
      return centerIcon(rect, icon)
    }
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

private fun centerIcon(rect: Rectangle, icon: Icon): Point {
  val x = rect.x + (rect.width - icon.iconWidth) / 2
  val y = rect.y + (rect.height - icon.iconHeight) / 2
  return Point(x, y)
}
