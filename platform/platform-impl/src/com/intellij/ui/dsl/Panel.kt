// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface Panel : RootPanel, CellBase<Panel> {

  override fun visible(isVisible: Boolean): Panel

  override fun horizontalAlign(horizontalAlign: HorizontalAlign): Panel

  override fun verticalAlign(verticalAlign: VerticalAlign): Panel

  override fun resizableColumn(): Panel

  override fun comment(comment: String, maxLineLength: Int): Panel

  override fun gap(rightGap: RightGap): Panel

  fun indent(init: Panel.() -> Unit): Panel
}
