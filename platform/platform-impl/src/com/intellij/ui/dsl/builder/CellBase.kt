// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.ui.dsl.gridLayout.*
import com.intellij.ui.layout.ComponentPredicate
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
@ApiStatus.NonExtendable
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
   * Binds cell visibility to provided [property] predicate
   */
  fun visibleIf(property: ObservableProperty<Boolean>): CellBase<T>

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
   * Binds cell enabled state to provided [property] predicate
   */
  fun enabledIf(property: ObservableProperty<Boolean>): CellBase<T>

  @Deprecated("Use align(AlignX.LEFT/CENTER/RIGHT/FILL) method instead", level = DeprecationLevel.ERROR)
  @ApiStatus.ScheduledForRemoval
  fun horizontalAlign(horizontalAlign: HorizontalAlign): CellBase<T>

  @Deprecated("Use align(AlignY.TOP/CENTER/BOTTOM/FILL) method instead", level = DeprecationLevel.ERROR)
  @ApiStatus.ScheduledForRemoval
  fun verticalAlign(verticalAlign: VerticalAlign): CellBase<T>

  /**
   * Updates horizontal and/or vertical alignment of the component inside the cell. To stretch the content on whole cell
   * use [AlignX.FILL]/[AlignY.FILL]/[Align.FILL]. For setting both horizontal and vertical alignment use [Align] constants or
   * overloaded plus operator like `align(AlignX.LEFT + AlignY.TOP)`. Default alignment is [AlignX.LEFT] + [AlignY.CENTER].
   *
   * In case the cell should occupy all available width or height in parent mark the column as [resizableColumn]
   * or the row as [Row.resizableRow] (or both if needed).
   *
   * @see [resizableColumn]
   * @see [Constraints.horizontalAlign]
   */
  fun align(align: Align): CellBase<T>

  /**
   * Marks column of the cell as resizable: the column occupies all extra horizontal space in parent and changes size together with parent.
   * It's possible to have several resizable columns, which means extra space is shared between them.
   * There is no need to set resizable for cells in different rows but in the same column: it has no additional effect.
   * Note that alignment inside the cell is managed by [align] method
   *
   * @see [Grid.resizableColumns]
   */
  fun resizableColumn(): CellBase<T>

  /**
   * Separates the next cell in the current row with [rightGap]. [RightGap.SMALL] gap is set after row label automatically
   * by [Panel.row] methods.
   * Right gap is ignored for the last cell in a row
   */
  fun gap(rightGap: RightGap): CellBase<T>

  /**
   * Overrides all gaps around the cell by [customGaps]. Should be used rarely for very specific cases
   */
  @Deprecated("Use customize(UnscaledGaps) instead", level = DeprecationLevel.HIDDEN)
  @ApiStatus.ScheduledForRemoval
  fun customize(customGaps: Gaps): CellBase<T>

  /**
   * Overrides all gaps around the cell by [customGaps]. Should be used rarely for very specific cases
   */
  fun customize(customGaps: UnscaledGaps): CellBase<T>

}
