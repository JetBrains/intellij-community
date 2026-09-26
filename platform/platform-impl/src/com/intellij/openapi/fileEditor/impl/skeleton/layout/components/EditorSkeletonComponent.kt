// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.layout.components

import com.intellij.ui.paint.use
import java.awt.Graphics2D

internal sealed class EditorSkeletonComponent {
  abstract val preferredWidth: Int
  abstract val preferredHeight: Int

  fun paint(g: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
    if (width <= 0 || height <= 0 || !g.hitClip(x, y, width, height)) return
    (g.create(x, y, width, height) as Graphics2D).use { graphics ->
      paintComponent(graphics, width, height)
    }
  }

  protected abstract fun paintComponent(g: Graphics2D, width: Int, height: Int)
}

internal class EditorSkeletonSpace(
  override val preferredWidth: Int,
  override val preferredHeight: Int,
) : EditorSkeletonComponent() {
  override fun paintComponent(g: Graphics2D, width: Int, height: Int) {}
}
