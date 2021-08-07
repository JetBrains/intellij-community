// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@DslMarker
private annotation class CellBuilderMarker

@ApiStatus.Experimental
@CellBuilderMarker
interface CellBuilderBase<T : CellBuilderBase<T>> {

  val horizontalAlign: HorizontalAlign
  val verticalAlign: VerticalAlign
  val rightGap: Int
  val comment: JComponent?

  fun alignHorizontal(horizontalAlign: HorizontalAlign): CellBuilderBase<T>

  fun alignVertical(verticalAlign: VerticalAlign): CellBuilderBase<T>

  fun comment(@NlsContexts.DetailedDescription comment: String,
              maxLineLength: Int = ComponentPanelBuilder.MAX_COMMENT_WIDTH): CellBuilderBase<T>

  /**
   * Separates next cell in current row with [SpacingConfiguration.horizontalUnrelatedGap].
   * Should not be used for last cell in a row
   */
  fun rightUnrelatedGap(): CellBuilderBase<T>

}
