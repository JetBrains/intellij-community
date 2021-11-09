// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.ui.ColorUtil
import com.intellij.util.SVGLoader
import com.intellij.util.SVGLoader.SvgElementColorPatcher
import com.intellij.util.SVGLoader.SvgElementColorPatcherProvider
import com.intellij.util.ui.JBValue
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.UIManager

/**
 * @author Konstantin Bulenkov
 */
class SquareStripeButtonLook(val button: ActionButton) : IdeaActionButtonLook() {
  override fun paintLookBackground(g: Graphics, rect: Rectangle, color: Color) {
    super.paintLookBackground(g, toSquareButtonRect(rect), getBackgroundColor(color))
  }

  private fun getBackgroundColor(color: Color): Color {
    if (button is SquareStripeButton) {
      if (button.isFocused()) return UIManager.getColor("ToolWindow.Button.selectedBackground")?: color
    }
    return color
  }

  private fun toSquareButtonRect(rect: Rectangle): Rectangle {
    val off = 5
    return Rectangle(rect.x + 5, rect.y + 5, rect.width - 2 * off, rect.height - 2 * off)
  }

  override fun paintIcon(g: Graphics?, actionButton: ActionButtonComponent?, icon: Icon?) {
    val color = UIManager.getColor("ToolWindow.Button.selectedForeground")
    if (actionButton is SquareStripeButton && actionButton.isFocused() && color != null) {
        try {
          val fg = ColorUtil.toHtmlColor(color)
          val map: Map<String, String> = mapOf("#767a8a" to fg,
                                               "#ced0d6" to fg,
                                               "#6e6e6e" to fg,
                                               "#afb1b3" to fg)
          val alpha: MutableMap<String, Int> = HashMap(map.size)
          map.forEach { (key: String?, value: String) -> alpha[value] = 255 }
          SVGLoader.setContextColorPatcher(object : SvgElementColorPatcherProvider {
            override fun forPath(path: String?): SvgElementColorPatcher? {
              return SVGLoader.newPatcher(null, map, alpha)
            }
          })
          SVGLoader.setColorRedefinitionContext(true)
          super.paintIcon(g, actionButton, icon)
          return
        } finally {
          SVGLoader.setColorRedefinitionContext(false)
          SVGLoader.setContextColorPatcher(null)
        }
      }


    super.paintIcon(g, actionButton, icon)
  }

  override fun paintLookBorder(g: Graphics, rect: Rectangle, color: Color) {
    if (button is SquareStripeButton && button.isFocused()) return
    super.paintLookBorder(g, toSquareButtonRect(rect), color)
  }

  override fun getButtonArc() = JBValue.UIInteger("Button.ToolWindow.arc", 12)
}
