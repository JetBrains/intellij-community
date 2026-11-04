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
}
