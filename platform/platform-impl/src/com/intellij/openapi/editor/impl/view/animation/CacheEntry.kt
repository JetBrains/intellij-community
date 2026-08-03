// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import com.intellij.util.ui.StartupUiUtil
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage

internal class CacheEntry(private val rectangle: Rectangle2D, private val image: BufferedImage) {
  val area: Double get() = rectangle.area

  fun contains(rectangle: Rectangle2D): Boolean = this.rectangle.contains(rectangle)

  fun intersects(rectangle: Rectangle2D): Boolean = this.rectangle.intersects(rectangle)

  fun paint(frameGraphics: Graphics2D) {
    // use translate because drawImage supports only integer coordinates
    frameGraphics.translate(rectangle.x, rectangle.y)
    StartupUiUtil.drawImage(frameGraphics, image, 0, 0, null)
    frameGraphics.translate(-rectangle.x, -rectangle.y)
  }

  override fun toString(): String {
    return "CacheEntry at $rectangle"
  }
}
