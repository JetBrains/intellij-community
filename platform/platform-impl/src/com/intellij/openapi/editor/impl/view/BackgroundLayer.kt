// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

/**
 * One background style of a painted block.
 *
 * While the editor rounds the corners of the highlighting, these backgrounds are rounded: the selection, an
 * [HighlighterTargetArea.EXACT_RANGE] highlighter, the fold placeholder, the guarded block and the syntax.
 * These backgrounds stay straight: a [HighlighterTargetArea.LINES_IN_RANGE] highlighter and the caret row.
 * Column mode follows the same rule.
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
     * Gives the background of the selection.
     */
    @JvmStatic
    fun selection(color: Color, rounded: Boolean, hugs: Boolean): BackgroundLayer {
      return BackgroundLayer(
        color = color,
        layer = HighlighterLayer.SELECTION,
        isRounded = rounded,
        hugs = hugs,
        forcesBackground = false,
        span = Int.MAX_VALUE,
      )
    }

    /**
     * Gives the background of a fold placeholder.
     */
    @JvmStatic
    fun foldPlaceholder(color: Color, rounded: Boolean): BackgroundLayer {
      return BackgroundLayer(
        color = color,
        layer = HighlighterLayer.GUARDED_BLOCKS,
        isRounded = rounded,
        hugs = false,
        forcesBackground = false,
        span = Int.MAX_VALUE,
      )
    }

    /**
     * Gives the background of a guarded block. It wins over every other background.
     */
    @JvmStatic
    fun guardedBlock(color: Color, rounded: Boolean): BackgroundLayer {
      return BackgroundLayer(
        color = color,
        layer = HighlighterLayer.GUARDED_BLOCKS,
        isRounded = rounded,
        hugs = false,
        forcesBackground = true,
        span = Int.MAX_VALUE,
      )
    }

    /**
     * Gives the background of the caret row. It is always straight.
     */
    @JvmStatic
    fun caretRow(color: Color): BackgroundLayer {
      return BackgroundLayer(
        color = color,
        layer = HighlighterLayer.CARET_ROW,
        isRounded = false,
        hugs = false,
        forcesBackground = false,
        span = Int.MAX_VALUE,
      )
    }

    /**
     * Gives the background of a syntax token.
     */
    @JvmStatic
    fun syntax(color: Color, rounded: Boolean): BackgroundLayer {
      return BackgroundLayer(
        color = color,
        layer = HighlighterLayer.SYNTAX,
        isRounded = rounded,
        hugs = false,
        forcesBackground = false,
        span = Int.MAX_VALUE,
      )
    }

    /**
     * Gives the background of a range highlighter, with its layer and the length of its affected area.
     * The editor decides with [roundedAllowed] whether it rounds at all.
     */
    @JvmStatic
    fun highlighter(highlighter: RangeHighlighterEx, color: Color, roundedAllowed: Boolean): BackgroundLayer {
      return BackgroundLayer(
        color = color,
        layer = highlighter.layer,
        isRounded = roundedAllowed && highlighter.targetArea == HighlighterTargetArea.EXACT_RANGE,
        hugs = false,
        forcesBackground = false,
        span = highlighter.affectedAreaEndOffset - highlighter.affectedAreaStartOffset,
      )
    }
  }
}
