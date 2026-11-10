// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import org.jetbrains.annotations.ApiStatus
import java.awt.Color

@ApiStatus.Internal
data class BackgroundLayer(
  val color: Color,
  val layer: Int,
  val isRounded: Boolean,
  val forcesBackground: Boolean,
  val span: Int,
) : Comparable<BackgroundLayer> {
  override fun compareTo(other: BackgroundLayer): Int {
    return compareValuesBy(this, other, { it.forcesBackground }, { it.layer }, { -it.span }, { it.color.rgb }, { it.isRounded })
  }

  companion object {
    @JvmField
    val STYLE_ORDER: Comparator<BackgroundLayer> = compareBy({ it.forcesBackground }, { it.layer }, { it.color.rgb }, { it.isRounded })
  }
}
