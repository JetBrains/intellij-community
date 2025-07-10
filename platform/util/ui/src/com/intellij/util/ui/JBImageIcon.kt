// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import javax.swing.ImageIcon

/**
 * HiDPI-aware image icon
 *
 * @author Konstantin Bulenkov
 */
open class JBImageIcon(image: Image) : ImageIcon(image) {
  @Synchronized
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    StartupUiUtil.drawImage(g, image, x, y, imageObserver ?: c)
  }
}
