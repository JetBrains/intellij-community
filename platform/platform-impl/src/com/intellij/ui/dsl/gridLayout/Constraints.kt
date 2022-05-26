// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.gridLayout

import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.checkNonNegative
import com.intellij.ui.dsl.checkPositive
import com.intellij.ui.dsl.gridLayout.impl.GridImpl
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

enum class HorizontalAlign {
  LEFT,
  CENTER,
  RIGHT,
  FILL
}

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
   * 1. All cells in the same grid row with [baselineAlign] true, [height] equals 1 and with the same [verticalAlign]
   * (except [VerticalAlign.FILL], which doesn't support baseline) are aligned by baseline together
   * 2. Sub grids (see [GridImpl.registerSubGrid]) with only one row and that contain cells only with [VerticalAlign.FILL] and another
   * specific [VerticalAlign] (at least one cell without fill align) have own baseline and can be aligned by baseline in parent grid
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
  var visualPaddings: Gaps = Gaps.EMPTY,

  /**
   * All components from the same width group will have the same width equals to maximum width from the group.
   * Cannot be used together with [HorizontalAlign.FILL] or for sub-grids (see [GridLayout.addLayoutSubGrid])
   */
  val widthGroup: String? = null,

  /**
   * Component helper for custom behaviour
   */
  @ApiStatus.Experimental
  val componentHelper: ComponentHelper? = null
) {

  init {
    checkNonNegative("x", x)
    checkNonNegative("y", y)
    checkPositive("width", width)
    checkPositive("height", height)

    if (widthGroup != null && horizontalAlign == HorizontalAlign.FILL) {
      throw UiDslException("Width group cannot be used with horizontal align FILL: $widthGroup")
    }
  }
}

/**
 * A helper for custom behaviour for components in cells
 */
@ApiStatus.Experimental
interface ComponentHelper {

  /**
   * Returns custom baseline or null if default baseline calculation should be used
   *
   * @see JComponent.getBaseline
   */
  fun getBaseline(width: Int, height: Int): Int?
}
