// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import java.awt.Graphics
import java.awt.Shape
import javax.swing.text.Element
import javax.swing.text.Position
import javax.swing.text.Segment
import javax.swing.text.View
import javax.swing.text.html.InlineView

class WbrView(elem: Element) : InlineView(elem) {

  override fun paint(g: Graphics?, a: Shape?) {
    // does not paint
  }

  override fun getMinimumSpan(axis: Int): Float = 0f

  override fun getMaximumSpan(axis: Int): Float =
    if (axis == X_AXIS)
      0f
    else
      Int.MAX_VALUE.toFloat()

  override fun getViewIndex(pos: Int, b: Position.Bias?): Int = -1

  override fun getViewIndex(x: Float, y: Float, allocation: Shape?): Int = -1

  override fun getResizeWeight(axis: Int): Int = 0

  override fun setSize(width: Float, height: Float) {
    super.setSize(0f, 0f)
  }

  override fun getPreferredSpan(axis: Int): Float =
    // We need to have a non-zero span for all measurements to work correctly.
    // However, it cannot be 1.0, because we would see a gap if <span> has background,
    // because we are not painting <wbr>. Let's return a pretty small number to avoid
    // any visible gap on higher resolutions and zooms
    if (axis == X_AXIS) 0.01f else 0f

  override fun getText(p0: Int, p1: Int): Segment = Segment()

  override fun breakView(axis: Int, offset: Int, pos: Float, len: Float): View = this

  override fun getBreakWeight(axis: Int, pos: Float, len: Float): Int = ExcellentBreakWeight
}
