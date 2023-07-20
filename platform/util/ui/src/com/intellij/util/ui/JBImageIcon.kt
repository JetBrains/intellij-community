// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    drawImage(g = g, image = image, x = x, y = y, observer = imageObserver ?: c)
  }
}
