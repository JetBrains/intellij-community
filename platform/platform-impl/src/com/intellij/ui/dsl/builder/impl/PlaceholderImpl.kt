// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.ui.dsl.builder.Placeholder
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class PlaceholderImpl(parent: RowImpl) : PlaceholderBaseImpl<Placeholder>(parent), Placeholder {

  override fun horizontalAlign(horizontalAlign: HorizontalAlign): Placeholder {
    super.horizontalAlign(horizontalAlign)
    return this
  }

  override fun verticalAlign(verticalAlign: VerticalAlign): Placeholder {
    super.verticalAlign(verticalAlign)
    return this
  }

  override fun resizableColumn(): Placeholder {
    super.resizableColumn()
    return this
  }

  override fun gap(rightGap: RightGap): Placeholder {
    super.gap(rightGap)
    return this
  }

  override fun enabled(isEnabled: Boolean): Placeholder {
    super.enabled(isEnabled)
    return this
  }

  override fun visible(isVisible: Boolean): Placeholder {
    super.visible(isVisible)
    return this
  }

  override fun customize(customGaps: Gaps): Placeholder {
    super.customize(customGaps)
    return this
  }
}
