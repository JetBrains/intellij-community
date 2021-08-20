// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import org.jetbrains.annotations.ApiStatus

/**
 * Used as a reserved space in layout. Can be populated by content later (not implemented yet)
 */
@ApiStatus.Experimental
@LayoutDslMarker
interface Placeholder : CellBase<Placeholder> {

  override fun visible(isVisible: Boolean): Placeholder

  override fun enabled(isEnabled: Boolean): Placeholder

  override fun horizontalAlign(horizontalAlign: HorizontalAlign): Placeholder

  override fun verticalAlign(verticalAlign: VerticalAlign): Placeholder

  override fun resizableColumn(): Placeholder

  override fun gap(rightGap: RightGap): Placeholder

}
