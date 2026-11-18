// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.layout.components

import java.awt.Graphics2D

internal enum class Layout { HORIZONTAL, VERTICAL }

internal enum class Side { LEFT, RIGHT }

internal class Padding(
  top: Int = 0,
  left: Int = 0,
  bottom: Int = 0,
  right: Int = 0,
  private val borderSide: Side? = null,
) {
  val topInset: Int = top
  val leftInset: Int = left + if (borderSide == Side.LEFT) 1 else 0
  private val rightInset: Int = right + if (borderSide == Side.RIGHT) 1 else 0
  val horizontal: Int = leftInset + rightInset
  val vertical: Int = top + bottom

  fun contentWidth(width: Int, x: Int = leftInset): Int {
    return (width - rightInset - x).coerceAtLeast(0)
  }

  fun contentHeight(height: Int): Int {
    return (height - vertical).coerceAtLeast(0)
  }

  fun paintBorder(g: Graphics2D, width: Int, height: Int) {
    when (borderSide) {
      Side.LEFT -> g.fillRect(0, 0, 1, height)
      Side.RIGHT -> g.fillRect(width - 1, 0, 1, height)
      null -> Unit
    }
  }
}
