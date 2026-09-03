// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.extendedToolWindowsUi.frontend

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.toolWindow.extendedToolWindowsUi.ToolWindowStripeExtension
import com.intellij.ui.IslandsState
import com.intellij.ui.util.height
import com.intellij.ui.util.width
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.Dimension

internal class ToolWindowStripeExtensionImpl : ToolWindowStripeExtension {

  override fun isStripeResizable(): Boolean {
    return false
  }

  override fun isToolWindowNameVisible(): Boolean {
    return true
  }

  override fun getStripeIconUnscaledSize(): Int {
    return ToolWindowStripeExtension.ICON_UNSCALED_SIZE
  }

  override fun getIconPadding(toolbarAnchor: ToolWindowAnchor): JBInsets {
    // Assume the paddings are symmetrical, so use the left padding as the reference
    // This allows customization and works well for both Islands and non-Islands themes
    val paddings = JBUI.CurrentTheme.Toolbar.stripeToolbarButtonIconPadding(true, false)
    val sidePadding = 4
    val left = paddings.unscaled.left
    val right = paddings.unscaled.right

    return when (toolbarAnchor) {
      ToolWindowAnchor.LEFT ->
        JBInsets(sidePadding, left, sidePadding, right)

      ToolWindowAnchor.RIGHT ->
        JBInsets(sidePadding, right, sidePadding, left) // Inverse

      ToolWindowAnchor.TOP ->
        JBInsets(left, sidePadding, right, sidePadding)

      ToolWindowAnchor.BOTTOM ->
        JBInsets(right, sidePadding, left, sidePadding)

      else -> JBInsets.emptyInsets()
    }
  }

  override fun getButtonMinSize(moreButton: Boolean): Dimension {
    val unscaledSize = getStripeButtonUnscaledSize()

    val heightCorrection: Int
    if (moreButton) {
      // The visible part of More button should be square
      val padding = getIconPadding(ToolWindowAnchor.LEFT).unscaled // should be the same correction with ToolWindowAnchorEnum.RIGHT
      heightCorrection = padding.height - padding.width
    }
    else {
      heightCorrection = 0
    }

    return JBDimension(unscaledSize, unscaledSize + heightCorrection)
  }

  private fun getStripeButtonUnscaledSize(): Int {
    var result = if (UISettings.getInstance().compactMode) 28 else 32
    if (!IslandsState.isEnabled()) {
      result += 3
    }
    return result
  }
}
