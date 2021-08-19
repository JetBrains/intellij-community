// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.impl

import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.CellBase
import com.intellij.ui.dsl.RightGap
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Experimental
internal sealed class CellBaseImpl<T : CellBase<T>>(private val dialogPanelConfig: DialogPanelConfig) : CellBase<T> {

  var horizontalAlign = HorizontalAlign.LEFT
    private set

  var verticalAlign = VerticalAlign.CENTER
    private set

  var resizableColumn = false
    private set

  var rightGap: RightGap? = null
    private set

  var comment: JComponent? = null
    private set

  override fun horizontalAlign(horizontalAlign: HorizontalAlign): CellBase<T> {
    this.horizontalAlign = horizontalAlign
    return this
  }

  override fun verticalAlign(verticalAlign: VerticalAlign): CellBase<T> {
    this.verticalAlign = verticalAlign
    return this
  }

  override fun resizableColumn(): CellBase<T> {
    this.resizableColumn = true
    return this
  }

  override fun comment(@NlsContexts.DetailedDescription comment: String?, maxLineLength: Int): CellBase<T> {
    this.comment = if (comment == null) null else ComponentPanelBuilder.createCommentComponent(comment, true, maxLineLength, true)
    return this
  }

  override fun gap(rightGap: RightGap): CellBase<T> {
    this.rightGap = rightGap
    return this
  }
}