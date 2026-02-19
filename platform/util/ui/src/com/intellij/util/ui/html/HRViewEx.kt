// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import java.awt.Rectangle
import javax.swing.text.Element
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

  override fun getViewAtPoint(x: Int, y: Int, alloc: Rectangle?): View? =
    null

  override fun getViewAtPosition(pos: Int, a: Rectangle?): View? =
    null

}