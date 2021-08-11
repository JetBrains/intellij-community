// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PanelBuilder : PanelBuilderBase, CellBuilderBase<PanelBuilder> {

  override fun horizontalAlign(horizontalAlign: HorizontalAlign): PanelBuilder

  override fun verticalAlign(verticalAlign: VerticalAlign): PanelBuilder

  override fun resizableColumn(): PanelBuilder

  override fun comment(comment: String, maxLineLength: Int): PanelBuilder

  override fun gap(rightGap: RightGap): PanelBuilder

}
