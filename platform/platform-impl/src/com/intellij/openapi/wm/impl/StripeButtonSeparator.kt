// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JComponent

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
class StripeButtonSeparator: JComponent() {
  var dndState: Boolean = false

  init {
    isOpaque = false
  }

  override fun getPreferredSize(): Dimension {
    return JBDimension(32, 11)
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val padding = SquareStripeButtonLook.getIconPadding(isOnTheLeftStripe())
    val fullWidth = this.width
    val fullHeight = this.height
    val visibleWidth = JBUI.scale(24)
    val height = JBUI.scale(1)
    val paintAreaWidth = fullWidth - (padding.left + padding.right)
    val x = padding.left + (paintAreaWidth - visibleWidth) / 2
    val y = fullHeight / 2
    g.color = JBUI.CurrentTheme.ToolWindow.stripeSeparatorColor(dndState)
    g.fillRect(x, y, visibleWidth, height)
  }
}