// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.util.ui.JBValue
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle

/**
 * @author Konstantin Bulenkov
 */
class SquareStripeButtonLook : IdeaActionButtonLook() {
  override fun paintLookBackground(g: Graphics, rect: Rectangle, color: Color) {
    super.paintLookBackground(g, toSquareButtonRect(rect), color)
  }

  private fun toSquareButtonRect(rect: Rectangle): Rectangle {
    val off = 5
    return Rectangle(rect.x + 5, rect.y + 5, rect.width - 2 * off, rect.height - 2 * off)
  }

  override fun paintLookBorder(g: Graphics, rect: Rectangle, color: Color) {
    super.paintLookBorder(g, toSquareButtonRect(rect), color)
  }

  override fun getButtonArc() = JBValue.UIInteger("Button.ToolWindow.arc", 12)
}
