// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.impl

import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.CellBuilderBase
import com.intellij.ui.dsl.SpacingConfiguration
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Experimental
internal open class CellBuilderBaseImpl<T : CellBuilderBase<T>>(private val dialogPanelConfig: DialogPanelConfig) : CellBuilderBase<T> {

  override var horizontalAlign = HorizontalAlign.LEFT
    protected set

  override var verticalAlign = VerticalAlign.CENTER
    protected set

  override var rightGap = 0
    protected set

  override var comment: JComponent? = null
    protected set

  override fun alignHorizontal(horizontalAlign: HorizontalAlign): CellBuilderBase<T> {
    this.horizontalAlign = horizontalAlign
    return this
  }

  override fun alignVertical(verticalAlign: VerticalAlign): CellBuilderBase<T> {
    this.verticalAlign = verticalAlign
    return this
  }

  override fun comment(@NlsContexts.DetailedDescription comment: String, maxLineLength: Int): CellBuilderBase<T> {
    this.comment = ComponentPanelBuilder.createCommentComponent(comment, true, maxLineLength, true)
    return this
  }

  /**
   * Separates next cell in current row with [SpacingConfiguration.horizontalUnrelatedGap]. Should not be used for last cell in a row
   */
  override fun rightUnrelatedGap(): CellBuilderBase<T> {
    rightGap = dialogPanelConfig.spacing.horizontalUnrelatedGap
    return this
  }
}