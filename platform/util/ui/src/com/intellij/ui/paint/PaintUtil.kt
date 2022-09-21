// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.paint

import java.awt.Graphics
import java.awt.Graphics2D

inline fun withTxAndClipAligned(
  g: Graphics,
  x: Int,
  y: Int,
  width: Int,
  height: Int,
  paintingCode: (Graphics2D) -> Unit
) {
  val aligned = g.create(x, y, width, height)
  try {
    aligned as Graphics2D
    alignToInt(aligned)
    paintingCode(aligned)
  }
  finally {
    aligned.dispose()
  }
}

fun alignToInt(g: Graphics) {
  if (g !is Graphics2D) {
    return
  }
  val rm = PaintUtil.RoundingMode.ROUND_FLOOR_BIAS
  PaintUtil.alignTxToInt(g, null, true, true, rm)
  PaintUtil.alignClipToInt(g, true, true, rm, rm)
}
