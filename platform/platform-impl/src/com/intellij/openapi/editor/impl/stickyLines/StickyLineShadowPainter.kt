// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.impl.ShadowPainter
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.JComponent

private val SHADOW_ICON = AllIcons.Ide.Shadow.BottomRight // workaround on rendering issue with Shadow.Bottom

internal class StickyLineShadowPainter {

  private val painter = ShadowPainter(
    top         = EmptyIcon,
    topRight    = EmptyIcon,
    right       = EmptyIcon,
    bottomRight = EmptyIcon,
    bottom      = SHADOW_ICON,
    bottomLeft  = EmptyIcon,
    left        = EmptyIcon,
    topLeft     = EmptyIcon,
  )

  private var shadow: BufferedImage? = null
  private var width: Int = 0
  private var height: Int = 0
  private var shadowHeight: Int = 0

  fun updateShadow(c: JComponent, newWidth: Int, newHeight: Int) {
    if (isEnabled() && newWidth != 0 && newHeight != 0 && c.graphicsConfiguration != null) {
      // paint 1/2 of icon as a workaround on rendering issue with Shadow.Bottom
      val newShadowHeight = SHADOW_ICON.iconHeight / 2
      if (shadow == null || width != newWidth || shadowHeight != newShadowHeight) {
        shadow = painter.createShadow(c, newWidth, newShadowHeight)
        width = newWidth
        shadowHeight = newShadowHeight
      }
      height = newHeight
    } else {
      shadow = null
    }
  }

  @Suppress("GraphicsSetClipInspection")
  fun paintShadow(g: Graphics?) {
    if (isEnabled()) {
      val shadow = shadow
      if (shadow != null && g is Graphics2D) {
        g.setClip(0, 0, width, height + shadowHeight)
        g.translate(0, height)
        UIUtil.drawImage(g, shadow, 0, 0, null)
        g.translate(0, -height)
        g.setClip(0, 0, width, height)
      }
    }
  }

  private fun isEnabled(): Boolean {
    return Registry.`is`("editor.show.sticky.lines.shadow", true)
  }

  private object EmptyIcon : Icon {
    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) = Unit
    override fun getIconWidth(): Int = 0
    override fun getIconHeight(): Int = 0
  }
}
