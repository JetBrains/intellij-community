// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.util.ui.JdkConstants.FontStyle
import java.awt.font.FontRenderContext

/**
 * Finds the font of a grapheme for [FontFallbackIterator].
 * The implementations are immutable and safe to use on any thread.
 */
internal interface FontLookup {
  /**
   * Returns a font that can display the code points of [text] from [start] to [end] in [style].
   * The returned font measures with [context].
   */
  fun fontAbleToDisplay(
    text: CharArray,
    start: Int,
    end: Int,
    @FontStyle style: Int,
    context: FontRenderContext?,
  ): FontInfo

  /**
   * Returns a font that can display the code points of [text] from [start] to [end] in [style].
   * The returned font measures with [context].
   */
  fun fontAbleToDisplay(
    text: CharSequence,
    start: Int,
    end: Int,
    @FontStyle style: Int,
    context: FontRenderContext?,
  ): FontInfo

  /**
   * Returns the font that [fontAbleToDisplay] gives for each printable ASCII char in [style] and [context],
   * or `null` when these chars have no common font or the lookup does not know it.
   * The printable ASCII chars are the chars from the space to `~`.
   */
  fun printableAsciiFont(@FontStyle style: Int, context: FontRenderContext?): FontInfo? {
    return null
  }
}
