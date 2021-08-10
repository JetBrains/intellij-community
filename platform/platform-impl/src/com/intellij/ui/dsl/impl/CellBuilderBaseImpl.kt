// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.impl

import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.CellBuilderBase
import com.intellij.ui.dsl.RIGHT_GAP_UNASSIGNED
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Experimental
// todo sealed
internal open class CellBuilderBaseImpl<T : CellBuilderBase<T>>(private val dialogPanelConfig: DialogPanelConfig) : CellBuilderBase<T> {

  override var horizontalAlign = HorizontalAlign.LEFT
    protected set

  override var verticalAlign = VerticalAlign.CENTER
    protected set

  override var resizableColumn = false
    protected set

  override var rightGap = RIGHT_GAP_UNASSIGNED
    protected set

  override var comment: JComponent? = null
    protected set

  override fun horizontalAlign(horizontalAlign: HorizontalAlign): CellBuilderBase<T> {
    this.horizontalAlign = horizontalAlign
    return this
  }

  override fun verticalAlign(verticalAlign: VerticalAlign): CellBuilderBase<T> {
    this.verticalAlign = verticalAlign
    return this
  }

  override fun resizableColumn(): CellBuilderBase<T> {
    this.resizableColumn = true
    return this
  }

  override fun comment(@NlsContexts.DetailedDescription comment: String, maxLineLength: Int): CellBuilderBase<T> {
    this.comment = ComponentPanelBuilder.createCommentComponent(comment, true, maxLineLength, true)
    return this
  }

  override fun rightLabelGap(): CellBuilderBase<T> {
    rightGap = dialogPanelConfig.spacing.horizontalLabelGap
    return this
  }
}