// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.markup

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.lang.ref.WeakReference


@Serializable
internal data class AdRangeHighlighterData(
  val originId: Long,
  val textAttributesKey: String?,
  val layer: Int,
  val isExactRange: Boolean,
  val isAfterEndOfLine: Boolean,
  val isVisibleIfFolded: Boolean,
  val isThinErrorStripeMark: Boolean,
  val isPersistent: Boolean,

  // TODO: WR is needed because of leaking AdRangeHighlighterData via DB
  @Transient private val origin: WeakReference<RangeHighlighterEx>? = null,
) {

  fun origin(): RangeHighlighterEx? {
    return origin?.get()
  }

  fun targetArea(): HighlighterTargetArea {
    return if (isExactRange) HighlighterTargetArea.EXACT_RANGE else HighlighterTargetArea.LINES_IN_RANGE
  }

  fun textAttributesKey(): TextAttributesKey? {
    return textAttributesKey?.let { TextAttributesKey.find(it) }
  }
}
