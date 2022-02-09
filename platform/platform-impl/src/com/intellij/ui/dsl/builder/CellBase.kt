// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ui.dsl.gridLayout.*
import com.intellij.ui.layout.*

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
   * Use [HorizontalAlign.FILL] to stretch the content on whole cell width. In case the cell should occupy all
   * available width in parent mark the column as [resizableColumn]
   *
   * @see [resizableColumn]
   * @see [Constraints.horizontalAlign]
   */
  fun horizontalAlign(horizontalAlign: HorizontalAlign): CellBase<T>

  /**
   * Sets vertical alignment of content inside the cell, [VerticalAlign.CENTER] by default
   * Use [VerticalAlign.FILL] to stretch the content on whole cell height. In case the cell should occupy all
   * available height in parent mark the row as [Row.resizableRow]
   *
   * @see [Row.resizableRow]
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

  /**
   * Overrides all gaps around the cell by [customGaps]. Should be used rarely for very specific cases
   */
  fun customize(customGaps: Gaps): CellBase<T>

}
