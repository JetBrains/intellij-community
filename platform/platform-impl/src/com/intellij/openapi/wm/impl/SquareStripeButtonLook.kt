// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import java.awt.Color
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.UIManager

/**
 * @author Konstantin Bulenkov
 */
internal class SquareStripeButtonLook(private val button: ActionButton) : IdeaActionButtonLook() {
  companion object {
    val ICON_PADDING: Insets
      get() = JBUI.insets(5)
  }

  override fun paintBackground(g: Graphics, component: JComponent, state: Int) {
    if (state == ActionButtonComponent.NORMAL && !component.isBackgroundSet) {
      return
    }
    val rect = Rectangle(component.size).also {
      JBInsets.removeFrom(it, component.insets)
      JBInsets.removeFrom(it, ICON_PADDING)
    }

    val color = getBackgroundColor(getStateBackground(component, state))
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
      JBInsets.removeFrom(it, ICON_PADDING)
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

  override fun paintIcon(g: Graphics?, actionButton: ActionButtonComponent?, icon: Icon) {
    val color = UIManager.getColor("ToolWindow.Button.selectedForeground")
    if (actionButton !is SquareStripeButton || !actionButton.isFocused() || color == null) {
      super.paintIcon(g, actionButton, icon)
      return
    }

    super.paintIcon(g, actionButton, IconUtil.toStrokeIcon(icon, UIManager.getColor("ToolWindow.Button.selectedForeground")))
  }

  override fun getButtonArc() = JBValue.UIInteger("Button.ToolWindow.arc", 12)
}
