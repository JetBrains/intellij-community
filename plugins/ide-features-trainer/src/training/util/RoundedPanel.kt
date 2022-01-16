// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.util

import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.RoundRectangle2D
import javax.swing.JPanel

class RoundedPanel(private val arcSize: Int) : JPanel() {
  override fun paintComponent(g: Graphics?) {
    val g2 = g as Graphics2D
    val rect = Rectangle(size)
    val rectangle2d = RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(),
      rect.width.toFloat(), rect.height.toFloat(), arcSize.toFloat(), arcSize.toFloat())
    g2.clip(rectangle2d)
    super.paintComponent(g)
  }
}