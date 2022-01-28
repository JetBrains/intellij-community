// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBInsets
import java.awt.*
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.plaf.basic.BasicGraphicsUtils
import javax.swing.plaf.metal.MetalToggleButtonUI

class StripeButtonUI : MetalToggleButtonUI() {
  companion object {
    val BACKGROUND_COLOR: Color = JBColor.namedColor("ToolWindow.Button.hoverBackground",
                                                     JBColor(Gray.x55.withAlpha(40), Gray.x0F.withAlpha(40)))
    val SELECTED_BACKGROUND_COLOR: Color = JBColor.namedColor("ToolWindow.Button.selectedBackground",
                                                              JBColor(Gray.x55.withAlpha(85), Gray.x0F.withAlpha(85)))
    val SELECTED_FOREGROUND_COLOR: Color = JBColor.namedColor("ToolWindow.Button.selectedForeground", JBColor(Gray.x00, Gray.xFF))
  }

  private val iconRect = Rectangle()
  private val textRect = Rectangle()
  private val viewRect = Rectangle()
  private var ourViewInsets: Insets = JBInsets.emptyInsets()

  override fun getPreferredSize(c: JComponent): Dimension {
    val button = c as AnchoredButton
    val dimension = super.getPreferredSize(button)
    dimension.width = (JBUIScale.scale(4) + dimension.width * 1.1f).toInt()
    dimension.height += JBUIScale.scale(2)
    val anchor = button.anchor
    return if (ToolWindowAnchor.LEFT == anchor || ToolWindowAnchor.RIGHT == anchor) {
      Dimension(dimension.height, dimension.width)
    }
    else {
      dimension
    }
  }

  override fun update(g: Graphics, c: JComponent) {
    val button = c as AnchoredButton
    val text = button.text
    val icon = (if (button.isEnabled) button.icon else button.disabledIcon)
    if (text == null && icon == null) {
      return
    }

    val fm = button.getFontMetrics(button.font)
    ourViewInsets = c.getInsets(ourViewInsets)
    viewRect.x = ourViewInsets.left
    viewRect.y = ourViewInsets.top

    val anchor = button.anchor
    // se inverted height & width
    if (ToolWindowAnchor.RIGHT == anchor || ToolWindowAnchor.LEFT == anchor) {
      viewRect.height = c.getWidth() - (ourViewInsets.left + ourViewInsets.right)
      viewRect.width = c.getHeight() - (ourViewInsets.top + ourViewInsets.bottom)
    }
    else {
      viewRect.height = c.getHeight() - (ourViewInsets.left + ourViewInsets.right)
      viewRect.width = c.getWidth() - (ourViewInsets.top + ourViewInsets.bottom)
    }
    iconRect.height = 0
    iconRect.width = iconRect.height
    iconRect.y = iconRect.width
    iconRect.x = iconRect.y
    textRect.height = 0
    textRect.width = textRect.height
    textRect.y = textRect.width
    textRect.x = textRect.y
    val clippedText = SwingUtilities.layoutCompoundLabel(
      c, fm, text, icon,
      button.verticalAlignment, button.horizontalAlignment,
      button.verticalTextPosition, button.horizontalTextPosition,
      viewRect, iconRect, textRect,
      if (button.text == null) 0 else button.iconTextGap
    )

    // Paint button's background
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
      val model = button.model
      val off = JBUIScale.scale(1)
      iconRect.x -= JBUIScale.scale(2)
      textRect.x -= JBUIScale.scale(2)
      if (model.isArmed && model.isPressed || model.isSelected || model.isRollover) {
        if (anchor == ToolWindowAnchor.LEFT) {
          g2.translate(-off, 0)
        }
        if (anchor.isHorizontal) {
          g2.translate(0, -off)
        }
        g2.color = if (model.isSelected) SELECTED_BACKGROUND_COLOR else BACKGROUND_COLOR
        g2.fillRect(0, 0, button.width, button.height)
        if (anchor == ToolWindowAnchor.LEFT) {
          g2.translate(off, 0)
        }
        if (anchor.isHorizontal) {
          g2.translate(0, off)
        }
      }
      if (ToolWindowAnchor.RIGHT == anchor || ToolWindowAnchor.LEFT == anchor) {
        if (ToolWindowAnchor.RIGHT == anchor) {
          icon?.paintIcon(c, g2, iconRect.y, iconRect.x)
          g2.rotate(Math.PI / 2)
          g2.translate(0, -c.getWidth())
        }
        else {
          icon?.paintIcon(c, g2, iconRect.y, c.getHeight() - iconRect.x - icon.iconHeight)
          g2.rotate(-Math.PI / 2)
          g2.translate(-c.getHeight(), 0)
        }
      }
      else {
        icon?.paintIcon(c, g2, iconRect.x, iconRect.y)
      }

      // paint text
      setupAntialiasing(g2)
      if (text != null) {
        if (model.isEnabled) {
          /* paint the text normally */
          g2.color = if (model.isSelected) SELECTED_FOREGROUND_COLOR else c.getForeground()
        }
        else {
          // paint the text disabled
          g2.color = getDisabledTextColor()
        }
        BasicGraphicsUtils.drawString(g2, clippedText, button.mnemonic2, textRect.x, textRect.y + fm.ascent)
      }
    }
    finally {
      g2.dispose()
    }
  }
}