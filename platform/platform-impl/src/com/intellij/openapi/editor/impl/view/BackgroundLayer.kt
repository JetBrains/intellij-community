// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.markup.HighlighterTargetArea
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

/**
 * One background style of a painted block.
 *
 * While the editor rounds the corners of the highlighting, these backgrounds are rounded: the selection, an
 * [HighlighterTargetArea.EXACT_RANGE] highlighter, the fold placeholder and the guarded block.
 * These backgrounds stay straight: a [HighlighterTargetArea.LINES_IN_RANGE] highlighter, the caret row and the syntax.
 * In column mode the editor does not round, so every background is straight.
 *
 * @property isRounded the paint rounds the corners of this background.
 * @property hugs the background stops at the text. It does not fill the line after the line end.
 */
@ApiStatus.Internal
data class BackgroundLayer(
  val color: Color,
  val layer: Int,
  val isRounded: Boolean,
  val hugs: Boolean,
  val forcesBackground: Boolean,
  val span: Int,
) : Comparable<BackgroundLayer> {
  override fun compareTo(other: BackgroundLayer): Int {
    var result = forcesBackground.compareTo(other.forcesBackground)
    if (result == 0) {
      result = layer.compareTo(other.layer)
    }
    if (result == 0) {
      result = other.span.compareTo(span)
    }
    if (result == 0) {
      result = compareStyles(this, other)
    }
    return result
  }

  companion object {
    @JvmField
    val STYLE_ORDER: Comparator<BackgroundLayer> = Comparator { first, second ->
      var result = first.forcesBackground.compareTo(second.forcesBackground)
      if (result == 0) {
        result = first.layer.compareTo(second.layer)
      }
      if (result == 0) {
        result = compareStyles(first, second)
      }
      result
    }

    private fun compareStyles(first: BackgroundLayer, second: BackgroundLayer): Int {
      var result = first.color.rgb.compareTo(second.color.rgb)
      if (result == 0) {
        result = first.isRounded.compareTo(second.isRounded)
      }
      if (result == 0) {
        result = first.hugs.compareTo(second.hugs)
      }
      return result
    }

    /**
     * Says that the paint rounds the corners of a highlighter background of this target area.
     * The editor decides with [roundedAllowed] whether it rounds at all.
     */
    @JvmStatic
    fun isRounded(targetArea: HighlighterTargetArea, roundedAllowed: Boolean): Boolean {
      return roundedAllowed && targetArea == HighlighterTargetArea.EXACT_RANGE
    }
  }
}
