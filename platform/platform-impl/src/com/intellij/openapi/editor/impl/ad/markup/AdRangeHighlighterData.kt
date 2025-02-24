// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.markup

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus.Experimental


@Experimental
@Serializable
internal data class AdRangeHighlighterData(
  val textAttributesKey: String,
  val layer: Int,
  val isExactRange: Boolean,
  val isAfterEndOfLine: Boolean,
  val isVisibleIfFolded: Boolean,
  @Transient val origin: RangeHighlighterEx? = null,
) {
  fun targetArea(): HighlighterTargetArea {
    return if (isExactRange) HighlighterTargetArea.EXACT_RANGE else HighlighterTargetArea.LINES_IN_RANGE
  }

  fun textAttributesKey(): TextAttributesKey? {
    return TextAttributesKey.find(textAttributesKey)
  }
}
