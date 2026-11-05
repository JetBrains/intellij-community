// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.markup.FontFamilies
import java.awt.Font
import java.awt.font.FontRenderContext

/**
 * Turns the font settings of one editor into fonts and character widths.
 * The text layout and the soft wraps read the same resolver, so they measure with the same fonts.
 *
 * The resolver also holds the resolved font families of the editor: the [FontFamilies] values that its text can have.
 * It keeps the [TextFonts] of each value. A reader gets them with [fonts] and never creates fonts.
 * An event resolves new values with [withFontFamilies], which gives a new resolver, see [EditorView.resolveFontFamilies].
 *
 * [EditorView] keeps one resolver in [EditorViewSnapshot].
 * A settings change and a render context change make the view build a new resolver for the same resolved values.
 * The resolver is immutable and safe to use on any thread.
 */
internal class EditorFontResolver private constructor(
  private val editorPreferences: FontPreferences,
  private val fontRenderContext: FontRenderContext,
  private val specialCharacterFont: Font?,
  private val fontFamiliesEnabled: Boolean,
  private val editorFonts: TextFonts,
  private val resolved: List<ResolvedFontFamilies>,
) {
  private val fontFamiliesAscent: Int = resolved.maxOfOrNull { it.fonts.ascent() } ?: 0

  private val fontFamiliesDescent: Int = resolved.maxOfOrNull { it.fonts.descent() } ?: 0

  private val fontFamiliesMaxCharWidth: Float = resolved.maxOfOrNull { it.fonts.maxCharWidth() } ?: 0f

  private val fontFamiliesMaxPrintableAsciiWidth: Float = resolved
    .filter { it.fonts !== editorFonts }
    .maxOfOrNull { it.fonts.maxPrintableAsciiWidth() } ?: 0f

  /**
   * Returns the fonts of text that has no font families of its own.
   */
  fun editorFonts(): TextFonts {
    return editorFonts
  }

  /**
   * Returns the fonts of text with [fontFamilies].
   * Returns the editor fonts for `null`, for a value that is not resolved, and for a value without installed families.
   * When the font families are off, see [com.intellij.openapi.editor.registry.EditorRegistry.isFontFamiliesEnabled],
   * each value gives the editor fonts.
   */
  fun fonts(fontFamilies: FontFamilies?): TextFonts {
    return resolvedFonts(fontFamilies) ?: editorFonts
  }

  /**
   * Returns the fonts of text with [fontFamilies], or `null` when the value is not resolved.
   * Returns the editor fonts for `null` and for a resolved value without installed families, see [fonts].
   * A reader that gets `null` uses the editor fonts and asks the editor to resolve the value later.
   */
  fun resolvedFonts(fontFamilies: FontFamilies?): TextFonts? {
    if (fontFamilies == null) {
      return editorFonts
    }
    return find(resolved, fontFamilies)?.fonts
  }

  /**
   * Tells whether [withFontFamilies] added [fontFamilies] to this resolver or to an earlier one.
   */
  fun isResolved(fontFamilies: FontFamilies): Boolean {
    return find(resolved, fontFamilies) != null
  }

  /**
   * Returns the resolved values, oldest first.
   */
  fun resolvedFontFamilies(): List<FontFamilies> {
    return resolved.map { it.fontFamilies }
  }

  /**
   * Returns a resolver that also has the fonts of [values], or this resolver when it already has all of them.
   * Values with the same installed families share one [TextFonts].
   * A value without installed families gets the editor fonts. It is resolved too, so that a reader does not report it again.
   */
  fun withFontFamilies(values: Collection<FontFamilies>): EditorFontResolver {
    var added: ArrayList<ResolvedFontFamilies>? = null
    for (value in values) {
      if (find(resolved, value) == null) {
        val current = added ?: ArrayList()
        if (find(current, value) == null) {
          current.add(createResolvedFontFamilies(value, current))
          added = current
        }
      }
    }
    if (added == null) {
      return this
    }
    return EditorFontResolver(
      editorPreferences = editorPreferences,
      fontRenderContext = fontRenderContext,
      specialCharacterFont = specialCharacterFont,
      fontFamiliesEnabled = fontFamiliesEnabled,
      editorFonts = editorFonts,
      resolved = java.util.List.copyOf(resolved + added),
    )
  }

  /**
   * Returns the largest ascent of the fonts of the resolved font families, or 0 without them.
   * The ascent includes the leading, see [TextFonts.ascent].
   */
  fun fontFamiliesAscent(): Int {
    return fontFamiliesAscent
  }

  /**
   * Returns the largest descent of the fonts of the resolved font families, or 0 without them.
   */
  fun fontFamiliesDescent(): Int {
    return fontFamiliesDescent
  }

  /**
   * Returns the largest [TextFonts.maxCharWidth] of the resolved font families, or 0 without them.
   */
  fun fontFamiliesMaxCharWidth(): Float {
    return fontFamiliesMaxCharWidth
  }

  /**
   * Returns an upper bound of the width of a printable ASCII character in the editor fonts and in the resolved font families.
   */
  fun maxPrintableAsciiWidth(): Float {
    return maxOf(editorFonts.maxPrintableAsciiWidth(), fontFamiliesMaxPrintableAsciiWidth)
  }

  /**
   * Returns the render context of all fonts of this resolver.
   */
  fun fontRenderContext(): FontRenderContext {
    return fontRenderContext
  }

  /**
   * Returns the font of the special character codes, or `null` when the editor does not show special characters.
   * See [SpecialCharacterFragment].
   */
  fun specialCharacterFont(): Font? {
    return specialCharacterFont
  }

  private fun createResolvedFontFamilies(value: FontFamilies, added: List<ResolvedFontFamilies>): ResolvedFontFamilies {
    val installedNames: List<String>
    if (fontFamiliesEnabled) {
      installedNames = value.names().filter { FontFamilyService.isAvailable(it) }
    } else {
      installedNames = listOf()
    }
    val fonts: TextFonts
    if (installedNames.isEmpty()) {
      fonts = editorFonts
    } else {
      val shared = sharedFonts(resolved, installedNames) ?: sharedFonts(added, installedNames)
      fonts = shared ?: TextFonts.forFontFamilies(installedNames, editorPreferences, fontRenderContext, specialCharacterFont)
    }
    return ResolvedFontFamilies(value, installedNames, fonts)
  }

  private fun sharedFonts(entries: List<ResolvedFontFamilies>, installedNames: List<String>): TextFonts? {
    return entries.firstOrNull { it.installedNames == installedNames }?.fonts
  }

  /**
   * A resolved value, the installed families of its names, in their order, and their fonts.
   */
  private class ResolvedFontFamilies(
    val fontFamilies: FontFamilies,
    val installedNames: List<String>,
    val fonts: TextFonts,
  )

  companion object {
    /**
     * Marks a snapshot that has no resolver yet.
     */
    @JvmField
    val UNINITIALIZED: EditorFontResolver = create(
      editorPreferences = FontPreferencesImpl(),
      fontRenderContext = FontInfo.getFontRenderContext(null),
      specialCharacterFont = null,
      fontFamiliesEnabled = false,
      fontFamilies = listOf(),
    )

    /**
     * Creates the resolver of the font settings of an editor, with the fonts of [fontFamilies].
     *
     * @param editorPreferences the font preferences of the editor
     * @param specialCharacterFont the font of the special character codes, or `null` when the editor does not show special characters
     * @param fontFamiliesEnabled whether the editor shows font families. When it does not, each value gives the editor fonts.
     */
    @JvmStatic
    fun create(
      editorPreferences: FontPreferences,
      fontRenderContext: FontRenderContext,
      specialCharacterFont: Font?,
      fontFamiliesEnabled: Boolean,
      fontFamilies: Collection<FontFamilies>,
    ): EditorFontResolver {
      val snapshot = editorPreferences.snapshot
      val editorFonts = TextFonts(snapshot, fontRenderContext, specialCharacterFont)
      val resolver = EditorFontResolver(
        editorPreferences = snapshot,
        fontRenderContext = fontRenderContext,
        specialCharacterFont = specialCharacterFont,
        fontFamiliesEnabled = fontFamiliesEnabled,
        editorFonts = editorFonts,
        resolved = listOf(),
      )
      return resolver.withFontFamilies(fontFamilies)
    }

    /**
     * Finds the resolved value. [FontFamilies] equality checks the identity first, so a lookup of the same object is fast.
     */
    private fun find(resolved: List<ResolvedFontFamilies>, value: FontFamilies): ResolvedFontFamilies? {
      for (entry in resolved) {
        if (entry.fontFamilies == value) {
          return entry
        }
      }
      return null
    }
  }
}
