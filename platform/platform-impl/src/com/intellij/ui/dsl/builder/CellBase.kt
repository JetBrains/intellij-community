// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ui.dsl.gridLayout.Constraints
import com.intellij.ui.dsl.gridLayout.Grid
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus

enum class RightGap {
  /**
   * See [SpacingConfiguration.horizontalSmallGap]
   */
  SMALL,

  /**
   * See [SpacingConfiguration.horizontalColumnsGap]
   */
  COLUMNS
}

/**
 * Common API for cells
 */
@ApiStatus.Experimental
@LayoutDslMarker
interface CellBase<out T : CellBase<T>> {

  /**
   * Sets visibility of the cell and all children recursively.
   * The cell is invisible if there is an invisible parent
   */
  fun visible(isVisible: Boolean): CellBase<T>

  /**
   * Binds cell visibility to provided [predicate]
   */
  fun visibleIf(predicate: ComponentPredicate): CellBase<T>

  /**
   * Sets enabled state of the cell and all children recursively.
   * The cell is disabled if there is a disabled parent
   */
  fun enabled(isEnabled: Boolean): CellBase<T>

  /**
   * Binds cell enabled state to provided [predicate]
   */
  fun enabledIf(predicate: ComponentPredicate): CellBase<T>

  /**
   * Sets horizontal alignment of content inside the cell, [HorizontalAlign.LEFT] by default.
   * Note that content width is usually less than cell width, use [HorizontalAlign.FILL] to stretch the content on whole cell width.
   *
   * @see [Constraints.horizontalAlign]
   */
  fun horizontalAlign(horizontalAlign: HorizontalAlign): CellBase<T>

  /**
   * Sets vertical alignment of content inside the cell, [VerticalAlign.CENTER] by default
   * Note that content height is usually less than cell height, use [VerticalAlign.FILL] to stretch the content on whole cell height.
   *
   * @see [Constraints.verticalAlign]
   */
  fun verticalAlign(verticalAlign: VerticalAlign): CellBase<T>

  /**
   * Marks column of the cell as resizable: the column occupies all extra horizontal space in parent and changes size together with parent.
   * It's possible to have several resizable columns, which means extra space is shared between them.
   * There is no need to set resizable for cells in different rows but in the same column: it has no additional effect.
   * Note that horizontal size and placement of component in columns are managed by [horizontalAlign]
   *
   * @see [Grid.resizableColumns]
   */
  fun resizableColumn(): CellBase<T>

  /**
   * Separates the next cell in the current row with [rightGap]. [RightGap.SMALL] gap is set after row label automatically
   * by [Panel.row] methods.
   * Should not be used for last cell in a row
   */
  fun gap(rightGap: RightGap): CellBase<T>

}
