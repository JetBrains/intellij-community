// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.layout.components

import java.awt.Graphics2D

internal class EditorSkeletonBlock(blockWidth: Width) : EditorSkeletonComponent() {
  override val preferredWidth: Int = blockWidth.width
  override val preferredHeight: Int = HEIGHT

  override fun paintComponent(g: Graphics2D, width: Int, height: Int) {
    g.fillRoundRect(0, 0, width, height, 2 * RADIUS, 2 * RADIUS)
  }

  enum class Width(val width: Int) {
    GUTTER_SMALL(10),
    GUTTER_NORMAL(16),
    SMALL(32),
    NORMAL(69),
    LARGE(184),
    EXTRA_LARGE(197),
  }

  companion object {
    const val HEIGHT = 16
    private const val RADIUS = 4
  }
}
