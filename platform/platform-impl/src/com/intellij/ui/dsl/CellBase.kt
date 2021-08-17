// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import org.jetbrains.annotations.ApiStatus

enum class RightGap {
  /**
   * See [SpacingConfiguration.horizontalSmallGap]
   */
  SMALL
}

@DslMarker
private annotation class CellBaseMarker

/**
 * Common API for cells
 */
@ApiStatus.Experimental
@LayoutDslMarker
interface CellBase<out T : CellBase<T>> {

  /**
   * Sets visibility for all components inside cell. Invisible state for all components is kept until the cell becomes visible again
   */
  fun visible(isVisible: Boolean): CellBase<T>

  /**
   * Sets enabled state for all components inside cell. Disabled state for all components is kept until the cell becomes enabled again
   */
  fun enabled(isEnabled: Boolean): CellBase<T>

  fun horizontalAlign(horizontalAlign: HorizontalAlign): CellBase<T>

  fun verticalAlign(verticalAlign: VerticalAlign): CellBase<T>

  /**
   * Marks column of the cell as resizable: the column occupies all extra space in panel and changes size together with panel.
   * It's possible to have several resizable columns, which means extra space is shared between them.
   * There is no need to set resizable for cells from one column: it has no effect
   */
  fun resizableColumn(): CellBase<T>

  fun comment(@NlsContexts.DetailedDescription comment: String,
              maxLineLength: Int = ComponentPanelBuilder.MAX_COMMENT_WIDTH): CellBase<T>

  /**
   * Separates next cell in current row with [rightGap]. [RightGap.SMALL] gap is set after row label automatically
   * by [Panel.row] methods.
   * Should not be used for last cell in a row
   */
  fun gap(rightGap: RightGap): CellBase<T>

}
