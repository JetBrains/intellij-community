// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import com.intellij.util.ui.accessibility.ScreenReader
import java.awt.Rectangle
import java.awt.Shape
import javax.swing.text.Element
import javax.swing.text.Position.Bias
import javax.swing.text.View

class HRViewEx(elem: Element, axis: Int) : BlockViewEx(elem, axis) {

  override fun getResizeWeight(axis: Int): Int =
    if (axis == X_AXIS)
      1
    else
      0

  override fun getPreferredSpan(axis: Int): Float =
    if (axis == View.X_AXIS)
      1f
    else
      super.getPreferredSpan(axis)

  override fun getBreakWeight(axis: Int, pos: Float, len: Float): Int =
    if (axis == X_AXIS)
      ForcedBreakWeight
    else
      BadBreakWeight

  override fun breakView(axis: Int, offset: Int, pos: Float, len: Float): View? =
    null

  @Suppress("DuplicatedCode")
  override fun modelToView(pos: Int, a: Shape, b: Bias?): Shape? {
    if (!ScreenReader.isActive()) return super.modelToView(pos, a, b)

    val p0 = startOffset
    val p1 = endOffset
    if (pos in p0..p1) {
      val r = a.bounds
      if (pos == p1) {
        r.x += r.width
      }
      r.width = 0
      return r
    }
    return null
  }

  override fun viewToModel(x: Float, y: Float, a: Shape?, bias: Array<Bias?>): Int {
    if (!ScreenReader.isActive()) return super.viewToModel(x, y, a, bias)

    val alloc = a as Rectangle
    if (x < alloc.x + (alloc.width / 2)) {
      bias[0] = Bias.Forward
      return startOffset
    }
    bias[0] = Bias.Backward
    return endOffset
  }

  override fun getViewAtPoint(x: Int, y: Int, alloc: Rectangle?): View? =
    null

  override fun getViewAtPosition(pos: Int, a: Rectangle?): View? =
    null

}