// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry
import com.intellij.util.ui.JdkConstants.FontStyle
import java.awt.Font
import java.awt.font.FontRenderContext
import java.util.concurrent.atomic.AtomicLongArray

/**
 * The fonts of text that an editor shows with one font setting, and the widths of its characters.
 * [EditorFontResolver] creates the instances.
 *
 * The instances are safe to use on any thread.
 * The width caches have no lock: each thread that misses a width measures it again, and all threads store the same value.
 *
 * @param preferences the font preferences for the fallback search. They are a snapshot, see [FontPreferences.getSnapshot].
 * @param specialCharacterFont the font of the special character codes, or `null` when the editor does not show special characters.
 *                             See [SpecialCharacterFragment].
 */
internal class TextFonts(
  private val preferences: FontPreferences,
  private val fontRenderContext: FontRenderContext,
  private val specialCharacterFont: Font?,
) {
  /** Only the soft wraps measure widths, so the caches exist only after the first measurement. */
  private val widthCaches by lazy { WidthCaches() }

  @Volatile
  private var maxPrintableAsciiWidth = -1f

  /**
   * Returns the font preferences for [com.intellij.openapi.editor.impl.FontFallbackIterator].
   */
  fun preferences(): FontPreferences {
    return preferences
  }

  /**
   * Returns the width of [codePoint] in [fontStyle], as the text layout of a single character gives it.
   */
  fun codePointWidth(codePoint: Int, @FontStyle fontStyle: Int): Float {
    val style = validStyle(fontStyle)
    val caches = widthCaches
    if (isPrintableAscii(codePoint)) {
      val asciiWidths = caches.asciiWidths
      val index = style * PRINTABLE_ASCII_COUNT + codePoint - FIRST_PRINTABLE_ASCII
      val cached = asciiWidths[index]
      if (cached >= 0) {
        return cached
      }
      val width = measure(codePoint, style)
      asciiWidths[index] = width
      return width
    }
    val otherWidths = caches.otherWidths
    val key = (style shl 21) or codePoint
    val index = mix(key) and (OTHER_WIDTHS_SIZE - 1)
    val entry = otherWidths.getOpaque(index)
    if (entry and USED_ENTRY != 0L && (entry ushr 32).toInt() and Int.MAX_VALUE == key) {
      return Float.fromBits(entry.toInt())
    }
    val width = measure(codePoint, style)
    otherWidths.setOpaque(index, USED_ENTRY or (key.toLong() shl 32) or (width.toRawBits().toLong() and 0xFFFFFFFFL))
    return width
  }

  /**
   * Returns an upper bound of [codePointWidth] for a printable ASCII character in any font style.
   * [isPrintableAscii] defines these characters.
   */
  fun maxPrintableAsciiWidth(): Float {
    val cached = maxPrintableAsciiWidth
    if (cached >= 0) {
      return cached
    }
    var maxWidth = 0f
    for (style in 0 until STYLE_COUNT) {
      for (codePoint in FIRST_PRINTABLE_ASCII..LAST_PRINTABLE_ASCII) {
        maxWidth = maxOf(maxWidth, codePointWidth(codePoint, style))
      }
    }
    maxPrintableAsciiWidth = maxWidth
    return maxWidth
  }

  private fun measure(codePoint: Int, @FontStyle fontStyle: Int): Float {
    if (specialCharacterFont != null) {
      // This is a simplification - we don't account for special characters not rendered in certain circumstances(based on surrounding
      // characters), so a premature wrapping can occur sometimes (as the representation using Unicode name is most certainly wider than the
      // original character).
      val width = SpecialCharacterFragment.getCodeWidth(specialCharacterFont, fontRenderContext, codePoint)
      if (width >= 0) {
        return width
      }
    }
    return ComplementaryFontsRegistry.getFontAbleToDisplay(codePoint, fontStyle, preferences, fontRenderContext).charWidth2D(codePoint)
  }

  /**
   * Maps a style that is not plain, bold, italic or bold italic to plain, as [ComplementaryFontsRegistry] does.
   */
  @FontStyle
  private fun validStyle(@FontStyle fontStyle: Int): Int {
    return if (fontStyle in 0 until STYLE_COUNT) fontStyle else Font.PLAIN
  }

  /**
   * Spreads the bits of [key], so that the code points of one script and their styles get different indices.
   */
  private fun mix(key: Int): Int {
    val hash = key * -0x61c88647 // the golden ratio constant
    return hash xor (hash ushr 16)
  }

  private class WidthCaches {
    /**
     * The widths of the printable ASCII characters in each style. A negative value is a width that is not measured yet.
     */
    val asciiWidths = FloatArray(STYLE_COUNT * PRINTABLE_ASCII_COUNT) { -1f }

    /**
     * The widths of other characters, in a lossy cache: a new width replaces the width of another key with the same index.
     * An entry holds [USED_ENTRY], the key in bits 32 to 62, and the bits of the width in bits 0 to 31.
     * An [AtomicLongArray] reads and writes each entry in one piece.
     */
    val otherWidths = AtomicLongArray(OTHER_WIDTHS_SIZE)
  }

  companion object {
    private const val FIRST_PRINTABLE_ASCII = '\u0020'.code // space
    private const val LAST_PRINTABLE_ASCII = '\u007E'.code // tilde
    private const val PRINTABLE_ASCII_COUNT = LAST_PRINTABLE_ASCII - FIRST_PRINTABLE_ASCII + 1
    private const val STYLE_COUNT = 4
    private const val OTHER_WIDTHS_SIZE = 1024 // a power of two

    /** Marks a used entry of the other widths, because the key 0 is a valid key. */
    private const val USED_ENTRY: Long = 1L shl 63

    /**
     * Returns `true` for a printable ASCII character, from the space to `'~'`.
     */
    @JvmStatic
    fun isPrintableAscii(codePoint: Int): Boolean {
      return codePoint in FIRST_PRINTABLE_ASCII..LAST_PRINTABLE_ASCII
    }
  }
}
