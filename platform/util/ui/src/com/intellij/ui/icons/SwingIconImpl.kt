// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import org.jetbrains.icons.api.PaintingApi
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

abstract class SwingIconImpl : SwingIcon {
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    render(SwingPaintingApi(c, g, x, y))
  }

  companion object {
    fun renderOldIcon(oldIcon: Icon, api: PaintingApi) {
      val swing = api.swing()
      if (oldIcon is org.jetbrains.icons.api.Icon) {
        oldIcon.render(api)
      } else if (swing != null) {
        oldIcon.paintIcon(swing.c, swing.g, swing.x, swing.y)
      } else error("Cannot render swing icon outside of swing.")
      // TODO Support fallback painting (for example off-screen render and send bitmap)
    }
  }
}
