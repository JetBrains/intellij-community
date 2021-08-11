// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

const val RIGHT_GAP_UNASSIGNED = -1

@DslMarker
private annotation class CellBuilderMarker

@ApiStatus.Experimental
@CellBuilderMarker
interface CellBuilderBase<out T : CellBuilderBase<T>> {

  fun horizontalAlign(horizontalAlign: HorizontalAlign): CellBuilderBase<T>

  fun verticalAlign(verticalAlign: VerticalAlign): CellBuilderBase<T>

  /**
   * Marks column of the cell as resizable: the column occupies all extra space in panel and changes size together with panel.
   * It's possible to have several resizable columns, which means extra space is shared between them.
   * There is no need to set resizable for cells from one column: it has no effect
   */
  fun resizableColumn(): CellBuilderBase<T>

  fun comment(@NlsContexts.DetailedDescription comment: String,
              maxLineLength: Int = ComponentPanelBuilder.MAX_COMMENT_WIDTH): CellBuilderBase<T>

  /**
   * Separates next cell in current row with [SpacingConfiguration.horizontalLabelGap]. This gap is set automatically
   * by [PanelBuilderBase.row] methods and should be used only in rare cases.
   * Should not be used for last cell in a row
   */
  fun rightLabelGap(): CellBuilderBase<T>

}
