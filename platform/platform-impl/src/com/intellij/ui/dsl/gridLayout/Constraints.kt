// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.gridLayout

import com.intellij.ui.dsl.checkNonNegative
import com.intellij.ui.dsl.checkPositive
import com.intellij.ui.dsl.gridLayout.impl.GridImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
enum class HorizontalAlign {
  LEFT,
  CENTER,
  RIGHT,
  FILL
}

@ApiStatus.Experimental
enum class VerticalAlign {
  TOP,
  CENTER,
  BOTTOM,
  FILL
}

@ApiStatus.Experimental
data class Constraints(

  /**
   * Grid destination
   */
  val grid: Grid,

  /**
   * Cell x coordinate in [grid]
   */
  val x: Int,

  /**
   * Cell y coordinate in [grid]
   */
  val y: Int,

  /**
   * Columns number occupied by the cell
   */
  val width: Int = 1,

  /**
   * Rows number occupied by the cell
   */
  val height: Int = 1,

  /**
   * Horizontal alignment of content inside the cell
   */
  val horizontalAlign: HorizontalAlign = HorizontalAlign.LEFT,

  /**
   * Vertical alignment of content inside the cell
   */
  val verticalAlign: VerticalAlign = VerticalAlign.CENTER,

  /**
   * If true then vertical align is done by baseline:
   *
   * 1. All components that have baselineAlign = true in the same row and with the same [verticalAlign] are aligned by baseline together
   * 2. Components are aligned even if they are placed in different sub grids (see [GridImpl.registerSubGrid])
   * 3. [VerticalAlign.FILL] alignment does not support [baselineAlign]
   * 4. Cells with [height] more than 1 do not support [baselineAlign]
   * 5. Only sub grids with one row can be aligned by baseline in parent grid
   */
  val baselineAlign: Boolean = false,

  /**
   * Gaps between grid cell bounds and components visual bounds (visual bounds is component bounds minus [visualPaddings])
   */
  val gaps: Gaps = Gaps.EMPTY,

  /**
   * Gaps between component bounds and its visual bounds. Can be used when component has focus ring outside of
   * its usual size. In such case components size is increased on focus size (so focus ring is not clipped)
   * and [visualPaddings] should be set to maintain right alignments
   *
   * 1. Layout manager aligns components by their visual bounds
   * 2. Cell size with gaps is calculated as component.bounds + [gaps] - [visualPaddings]
   */
  var visualPaddings: Gaps = Gaps.EMPTY
) {

  init {
    checkNonNegative(::x)
    checkNonNegative(::y)
    checkPositive(::width)
    checkPositive(::height)
  }
}
