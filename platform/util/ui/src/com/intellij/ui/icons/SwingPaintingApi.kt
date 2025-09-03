// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import org.jetbrains.icons.api.BitmapImageResource
import org.jetbrains.icons.api.Bounds
import org.jetbrains.icons.api.PaintingApi
import java.awt.Component
import java.awt.Graphics

class SwingPaintingApi(
  val c: Component?,
  val g: Graphics,
  val x: Int,
  val y: Int
) : PaintingApi {
  override val bounds: Bounds get() {
    if (c == null) return Bounds(0, 0)
    return Bounds(
      c.width,
      c.height
    )
  }

  override fun drawImage(image: BitmapImageResource, x: Int, y: Int, width: Int?, height: Int?) {
    val swingImage = image as? AwtImageResource ?: error("Image resource must be AwtImageResource to be rendered using swing.")
    if (width != null && height != null) {
      g.drawImage(swingImage.image, x, y, width, height, null)
    } else {
      g.drawImage(swingImage.image, x, y, null)
    }
  }
}

fun PaintingApi.swing(): SwingPaintingApi? = this as? SwingPaintingApi