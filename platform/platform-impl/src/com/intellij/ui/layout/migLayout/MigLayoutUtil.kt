// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout.migLayout

import net.miginfocom.layout.BoundSize
import net.miginfocom.layout.LC
import net.miginfocom.layout.UnitValue

@Deprecated("Mig Layout is going to be removed, IDEA-306719")
fun createLayoutConstraints(horizontalGap: Int, verticalGap: Int): LC {
  val lc = LC()
  lc.gridGapX = gapToBoundSize(horizontalGap, isHorizontal = true)
  lc.gridGapY = gapToBoundSize(verticalGap, isHorizontal = false)
  lc.setInsets(0)
  return lc
}

@Deprecated("Mig Layout is going to be removed, IDEA-306719")
fun gapToBoundSize(value: Int, isHorizontal: Boolean): BoundSize {
  val unitValue = createUnitValue(value, isHorizontal)
  return BoundSize(unitValue, unitValue, null, false, null)
}

@Deprecated("Mig Layout is going to be removed, IDEA-306719")
fun createLayoutConstraints(): LC {
  val lc = LC()
  lc.gridGapX = gapToBoundSize(0, true)
  lc.setInsets(0)
  return lc
}

@Deprecated("Mig Layout is going to be removed, IDEA-306719")
internal fun LC.setInsets(value: Int) = setInsets(value, value)

@Deprecated("Mig Layout is going to be removed, IDEA-306719")
internal fun LC.setInsets(topBottom: Int, leftRight: Int) {
  val h = createUnitValue(leftRight, isHorizontal = true)
  val v = createUnitValue(topBottom, isHorizontal = false)
  insets = arrayOf(v, h, v, h)
}

@Deprecated("Mig Layout is going to be removed, IDEA-306719")
fun createUnitValue(value: Int, isHorizontal: Boolean): UnitValue {
  return UnitValue(value.toFloat(), "px", isHorizontal, UnitValue.STATIC, null)
}