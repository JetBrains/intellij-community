// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry
import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.impl.FontLookup
import com.intellij.openapi.editor.impl.RegistryFontLookup
import com.intellij.util.text.CharArrayUtil
import com.intellij.util.ui.JdkConstants.FontStyle
import java.awt.Font
import java.awt.font.FontRenderContext
import java.util.concurrent.atomic.AtomicLongArray

/**
 * The fonts of text that an editor shows with one font setting, and the widths of its characters.
 * [EditorFontResolver] creates the instances.
 * As a [FontLookup], the instance finds the fonts for [com.intellij.openapi.editor.impl.FontFallbackIterator].
 *
 * The fonts are a chain: the font families of the text, in their order, and then the editor fonts.
 * An instance without font families has only the editor fonts, see the constructor.
 * The font families are the fonts of a [com.intellij.openapi.editor.markup.FontFamilies] value, see [forFontFamilies].
 *
 * The instances are safe to use on any thread.
 * The caches have no lock: each thread that misses a value computes it again, and all threads store the same value.
 */
internal class TextFonts private constructor(
  preferences: FontPreferences,
  private val fontRenderContext: FontRenderContext,
  private val specialCharacterFont: Font?,
  private val families: List<FamilyFonts>,
) : FontLookup {
  /**
   * Creates the editor fonts.
   *
   * @param preferences the font preferences of the editor fonts
   * @param specialCharacterFont the font of the special character codes, or `null` when the editor does not show special characters.
   *                             See [SpecialCharacterFragment].
   */
  constructor(
    preferences: FontPreferences,
    fontRenderContext: FontRenderContext,
    specialCharacterFont: Font?,
  ) : this(preferences, fontRenderContext, specialCharacterFont, listOf())

  /** Finds the editor fonts, the end of the chain. */
  private val editorFontLookup = RegistryFontLookup(preferences)

  /** The caches exist only after the first measurement, because only the soft wraps and [forFontFamilies] measure widths. */
  private val widthCaches by lazy { WidthCaches() }

  /** The font family of each code point that is not ASCII, in a lossy cache. See [familyIndex]. */
  private val familyIndices by lazy { AtomicLongArray(FAMILY_INDICES_SIZE) }

  /** The editor font of each ASCII char in each style, for [fontRenderContext]. See [editorAsciiFont]. */
  private val editorAsciiFonts = arrayOfNulls<FontInfo>(STYLE_COUNT * FIRST_NON_ASCII)

  @Volatile private var maxPrintableAsciiWidth = -1f

  private val ascent: Int = families.maxOfOrNull { family -> family.fonts.maxOf { fontAscent(it) } } ?: 0

  private val descent: Int = families.maxOfOrNull { family -> family.fonts.maxOf { fontDescent(it) } } ?: 0

  private val maxCharWidth: Float = families.firstOrNull()?.fonts?.get(Font.BOLD or Font.ITALIC)?.charWidth2D('W'.code) ?: 0f

  override fun fontAbleToDisplay(
    text: CharArray,
    start: Int,
    end: Int,
    @FontStyle style: Int,
    context: FontRenderContext?,
  ): FontInfo {
    if (families.isNotEmpty()) {
      val validStyle = validStyle(style)
      val index = graphemeFamilyIndex(text, start, end, validStyle)
      if (index >= 0) {
        return familyFont(index, validStyle, context)
      }
    }
    if (end - start == 1 && text[start].code < FIRST_NON_ASCII && context === fontRenderContext) {
      return editorAsciiFont(text[start].code, style)
    }
    return editorFontLookup.fontAbleToDisplay(text, start, end, style, context)
  }

  override fun fontAbleToDisplay(
    text: CharSequence,
    start: Int,
    end: Int,
    @FontStyle style: Int,
    context: FontRenderContext?,
  ): FontInfo {
    if (families.isNotEmpty()) {
      val validStyle = validStyle(style)
      val index = graphemeFamilyIndex(text, start, end, validStyle)
      if (index >= 0) {
        return familyFont(index, validStyle, context)
      }
    }
    if (end - start == 1 && text[start].code < FIRST_NON_ASCII && context === fontRenderContext) {
      return editorAsciiFont(text[start].code, style)
    }
    return editorFontLookup.fontAbleToDisplay(text, start, end, style, context)
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
    val key = cacheKey(codePoint, style)
    val index = mix(key) and (OTHER_WIDTHS_SIZE - 1)
    val entry = otherWidths.getOpaque(index)
    if (entryMatches(entry, key)) {
      return Float.fromBits(entry.toInt())
    }
    val width = measure(codePoint, style)
    otherWidths.setOpaque(index, cacheEntry(key, width.toRawBits()))
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

  /**
   * Returns the largest ascent of the fonts of the font families, or 0 for the editor fonts.
   * The ascent includes the leading: it is the font height minus the descent, as in [EditorViewMetrics].
   */
  fun ascent(): Int {
    return ascent
  }

  /**
   * Returns the largest descent of the fonts of the font families, or 0 for the editor fonts.
   * The metrics of the editor fonts are in [EditorViewMetrics].
   */
  fun descent(): Int {
    return descent
  }

  /**
   * Returns the width of a bold italic `W` in the first font family, or 0 for the editor fonts.
   * The width estimates before the layout use it, as [EditorViewMetrics] does for the editor fonts.
   */
  fun maxCharWidth(): Float {
    return maxCharWidth
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
    return fontAbleToDisplay(codePoint, fontStyle).charWidth2D(codePoint)
  }

  /**
   * Returns the editor font of the ASCII char [codePoint] in [style], for [fontRenderContext].
   * It is the font that the registry gives, see [RegistryFontLookup].
   */
  private fun editorAsciiFont(codePoint: Int, @FontStyle style: Int): FontInfo {
    val validStyle = validStyle(style)
    val index = validStyle * FIRST_NON_ASCII + codePoint
    val cached = editorAsciiFonts[index]
    if (cached != null) {
      return cached
    }
    val font = editorFontLookup.fontAbleToDisplay(codePoint, validStyle, fontRenderContext)
    editorAsciiFonts[index] = font
    return font
  }

  private fun fontAbleToDisplay(codePoint: Int, @FontStyle fontStyle: Int): FontInfo {
    if (families.isNotEmpty()) {
      val index = familyIndex(codePoint, fontStyle)
      if (index >= 0) {
        return familyFont(index, fontStyle, fontRenderContext)
      }
    }
    return editorFontLookup.fontAbleToDisplay(codePoint, fontStyle, fontRenderContext)
  }

  /**
   * Returns the index of the first font family that displays the grapheme of [text] from [start] to [end],
   * or -1 when no font family displays it.
   */
  private fun graphemeFamilyIndex(text: CharArray, start: Int, end: Int, @FontStyle style: Int): Int {
    val codePoint = Character.codePointAt(text, start, end)
    val next = start + Character.charCount(codePoint)
    if (next >= end) {
      return familyIndex(codePoint, style)
    }
    return familyIndex(codePoint, style, text, next, end)
  }

  /**
   * Returns the index of the first font family that displays the grapheme of [text] from [start] to [end],
   * or -1 when no font family displays it.
   * A grapheme of one char is one code point, as in [ComplementaryFontsRegistry].
   */
  private fun graphemeFamilyIndex(text: CharSequence, start: Int, end: Int, @FontStyle style: Int): Int {
    val codePoint = if (end - start == 1) text[start].code else Character.codePointAt(text, start)
    val next = start + Character.charCount(codePoint)
    if (next >= end) {
      return familyIndex(codePoint, style)
    }
    val rest = CharArrayUtil.fromSequence(text, next, end)
    return familyIndex(codePoint, style, rest, 0, rest.size)
  }

  /**
   * Returns the index of the first font family that displays [codePoint] in [style], or -1 when no font family displays it.
   * A code point below 128 always gets the first font family, as in [ComplementaryFontsRegistry].
   */
  private fun familyIndex(codePoint: Int, @FontStyle style: Int): Int {
    if (codePoint < FIRST_NON_ASCII) {
      return 0
    }
    val indices = familyIndices
    val key = cacheKey(codePoint, style)
    val slot = mix(key) and (FAMILY_INDICES_SIZE - 1)
    val entry = indices.getOpaque(slot)
    if (entryMatches(entry, key)) {
      return entry.toInt()
    }
    var index = -1
    for (i in families.indices) {
      if (FontInfo.canDisplay(families[i].fonts[style].font, codePoint, true)) {
        index = i
        break
      }
    }
    indices.setOpaque(slot, cacheEntry(key, index))
    return index
  }

  /**
   * Returns the index of the first font family that displays [codePoint] and the code points of [rest] from [restStart] to [restEnd],
   * or -1 when no font family displays them.
   * The families before the family of [codePoint] cannot display it, so the search starts there.
   */
  private fun familyIndex(codePoint: Int, @FontStyle style: Int, rest: CharArray, restStart: Int, restEnd: Int): Int {
    val first = familyIndex(codePoint, style)
    if (first < 0) {
      return -1
    }
    for (i in first until families.size) {
      val font = families[i].fonts[style].font
      val displaysCodePoint = i == first || codePoint < FIRST_NON_ASCII || FontInfo.canDisplay(font, codePoint, true)
      if (displaysCodePoint && font.canDisplayUpTo(rest, restStart, restEnd) == -1) {
        return i
      }
    }
    return -1
  }

  /**
   * Returns the font of the font family at [index] in [style], for [context].
   * The family has its fonts for the render context of this instance. For another context, the registry gives the font.
   */
  private fun familyFont(index: Int, @FontStyle style: Int, context: FontRenderContext?): FontInfo {
    val family = families[index]
    val font = family.fonts[style]
    if (context == fontRenderContext) {
      return font
    }
    return ComplementaryFontsRegistry.getFamilyFontInfo(family.name, style, font.size2D, family.useLigatures, family.letterSpacing, context)
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

  /**
   * The fonts of one font family at the editor size, one for each style.
   *
   * @param useLigatures whether the fonts use ligatures. A monospaced family follows the editor setting, a proportional one has none.
   * @param letterSpacing the letter spacing of the editor, see [FontPreferences.getLetterSpacing]
   */
  private class FamilyFonts(
    val name: String,
    val useLigatures: Boolean,
    val letterSpacing: Float,
    val fonts: List<FontInfo>,
  )

  private class WidthCaches {
    /**
     * The widths of the printable ASCII characters in each style. A negative value is a width that is not measured yet.
     */
    val asciiWidths = FloatArray(STYLE_COUNT * PRINTABLE_ASCII_COUNT) { -1f }

    /**
     * The widths of other characters, in a lossy cache: a new width replaces the width of another key with the same index.
     * An entry holds the bits of the width, see [cacheEntry].
     */
    val otherWidths = AtomicLongArray(OTHER_WIDTHS_SIZE)
  }

  companion object {
    private const val FIRST_PRINTABLE_ASCII = '\u0020'.code // space
    private const val LAST_PRINTABLE_ASCII = '\u007E'.code // tilde
    private const val PRINTABLE_ASCII_COUNT = LAST_PRINTABLE_ASCII - FIRST_PRINTABLE_ASCII + 1
    private const val FIRST_NON_ASCII = 128
    private const val STYLE_COUNT = 4
    private const val OTHER_WIDTHS_SIZE = 1024 // a power of two

    /**
     * The size of the lossy cache of the font family indices, a power of two.
     * An entry holds the index, where -1 means the editor fonts, see [cacheEntry].
     */
    private const val FAMILY_INDICES_SIZE = 1024

    /** Marks a used entry of a lossy cache, because the key 0 is a valid key. */
    private const val USED_ENTRY: Long = 1L shl 63

    /**
     * Returns `true` for a printable ASCII character, from the space to `'~'`.
     */
    @JvmStatic
    fun isPrintableAscii(codePoint: Int): Boolean {
      return codePoint in FIRST_PRINTABLE_ASCII..LAST_PRINTABLE_ASCII
    }

    /**
     * Creates the fonts of text with font families: the families, in their order, and then the editor fonts.
     * The families use the editor size and the editor letter spacing. Their fonts have no character variants.
     * The bounds of the families, including [maxPrintableAsciiWidth], are ready after the creation.
     *
     * @param familyNames the names of the font families, see [FontFamilyService.isAvailable]. The list is not empty.
     * @param preferences the font preferences of the editor fonts
     * @param specialCharacterFont the font of the special character codes, or `null` when the editor does not show special characters.
     * @throws IllegalArgumentException when [familyNames] is empty
     */
    fun forFontFamilies(
      familyNames: List<String>,
      preferences: FontPreferences,
      fontRenderContext: FontRenderContext,
      specialCharacterFont: Font?,
    ): TextFonts {
      requireFamilyNames(familyNames)
      val snapshot = preferences.snapshot
      val size = snapshot.getSize2D(snapshot.fontFamily)
      val letterSpacing = snapshot.letterSpacing
      val families = familyNames.map { name ->
        val useLigatures = snapshot.useLigatures() && FontFamilyService.isMonospaced(name)
        val fonts = (0 until STYLE_COUNT).map { style ->
          ComplementaryFontsRegistry.getFamilyFontInfo(name, style, size, useLigatures, letterSpacing, fontRenderContext)
        }
        FamilyFonts(name, useLigatures, letterSpacing, java.util.List.copyOf(fonts))
      }
      val textFonts = TextFonts(snapshot, fontRenderContext, specialCharacterFont, java.util.List.copyOf(families))
      textFonts.maxPrintableAsciiWidth()
      return textFonts
    }

    private fun requireFamilyNames(familyNames: List<String>) {
      require(familyNames.isNotEmpty()) { "No font family names" }
    }

    private fun fontAscent(font: FontInfo): Int {
      val metrics = font.fontMetrics()
      val layoutService = FontLayoutService.getInstance()
      return layoutService.getHeight(metrics) - layoutService.getDescent(metrics)
    }

    private fun fontDescent(font: FontInfo): Int {
      return FontLayoutService.getInstance().getDescent(font.fontMetrics())
    }

    /**
     * Returns the key of a lossy cache entry: the style in bits 21 and 22, and the code point in bits 0 to 20.
     */
    private fun cacheKey(codePoint: Int, @FontStyle style: Int): Int {
      return (style shl 21) or codePoint
    }

    /**
     * Returns a lossy cache entry: [USED_ENTRY], [key] in bits 32 to 62, and [value] in bits 0 to 31.
     * An [AtomicLongArray] reads and writes each entry in one piece.
     */
    private fun cacheEntry(key: Int, value: Int): Long {
      return USED_ENTRY or (key.toLong() shl 32) or (value.toLong() and 0xFFFFFFFFL)
    }

    private fun entryMatches(entry: Long, key: Int): Boolean {
      return (entry and USED_ENTRY) != 0L && ((entry ushr 32).toInt() and Int.MAX_VALUE) == key
    }
  }
}
