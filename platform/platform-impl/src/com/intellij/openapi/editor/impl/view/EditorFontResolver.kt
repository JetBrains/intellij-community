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
 * The resolver also holds the published font families of the editor: the [FontFamilies] values that its text can have.
 * It keeps the [TextFonts] of each value. A reader gets them with [fonts] and never creates fonts.
 * An event publishes new values with [withFontFamilies], which gives a new resolver, see [EditorView.publishFontFamilies].
 *
 * [EditorView] keeps one resolver in [EditorViewSnapshot].
 * A settings change and a render context change make the view build a new resolver for the same published values.
 * The resolver is immutable and safe to use on any thread.
 */
internal class EditorFontResolver private constructor(
  private val editorPreferences: FontPreferences,
  private val fontRenderContext: FontRenderContext,
  private val specialCharacterFont: Font?,
  private val fontFamiliesEnabled: Boolean,
  private val editorFonts: TextFonts,
  private val published: List<PublishedFontFamilies>,
) {
  private val fontFamiliesAscent: Int = published.maxOfOrNull { it.fonts.ascent() } ?: 0

  private val fontFamiliesDescent: Int = published.maxOfOrNull { it.fonts.descent() } ?: 0

  private val fontFamiliesMaxCharWidth: Float = published.maxOfOrNull { it.fonts.maxCharWidth() } ?: 0f

  private val fontFamiliesMaxPrintableAsciiWidth: Float = published
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
   * Returns the editor fonts for `null`, for a value that is not published, and for a value without installed families.
   * When the font families are off, see [com.intellij.openapi.editor.registry.EditorRegistry.isFontFamiliesEnabled],
   * each value gives the editor fonts.
   */
  fun fonts(fontFamilies: FontFamilies?): TextFonts {
    if (fontFamilies == null) {
      return editorFonts
    }
    return find(published, fontFamilies)?.fonts ?: editorFonts
  }

  /**
   * Tells whether [withFontFamilies] added [fontFamilies] to this resolver or to an earlier one.
   */
  fun isPublished(fontFamilies: FontFamilies): Boolean {
    return find(published, fontFamilies) != null
  }

  /**
   * Returns the published values, in the order of their publication.
   */
  fun publishedFontFamilies(): List<FontFamilies> {
    return published.map { it.fontFamilies }
  }

  /**
   * Returns a resolver that also has the fonts of [values], or this resolver when it already has all of them.
   * Values with the same installed families share one [TextFonts].
   * A value without installed families gets the editor fonts. It is published too, so that a reader does not report it again.
   */
  fun withFontFamilies(values: Collection<FontFamilies>): EditorFontResolver {
    val added = ArrayList<PublishedFontFamilies>()
    for (value in values) {
      if (find(published, value) == null && find(added, value) == null) {
        added.add(createPublishedFontFamilies(value, added))
      }
    }
    if (added.isEmpty()) {
      return this
    }
    return EditorFontResolver(
      editorPreferences = editorPreferences,
      fontRenderContext = fontRenderContext,
      specialCharacterFont = specialCharacterFont,
      fontFamiliesEnabled = fontFamiliesEnabled,
      editorFonts = editorFonts,
      published = java.util.List.copyOf(published + added),
    )
  }

  /**
   * Returns the largest ascent of the fonts of the published font families, or 0 without them.
   * The ascent includes the leading, see [TextFonts.ascent].
   */
  fun fontFamiliesAscent(): Int {
    return fontFamiliesAscent
  }

  /**
   * Returns the largest descent of the fonts of the published font families, or 0 without them.
   */
  fun fontFamiliesDescent(): Int {
    return fontFamiliesDescent
  }

  /**
   * Returns the largest [TextFonts.maxCharWidth] of the published font families, or 0 without them.
   */
  fun fontFamiliesMaxCharWidth(): Float {
    return fontFamiliesMaxCharWidth
  }

  /**
   * Returns an upper bound of the width of a printable ASCII character in the editor fonts and in the published font families.
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

  private fun createPublishedFontFamilies(value: FontFamilies, added: List<PublishedFontFamilies>): PublishedFontFamilies {
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
      val shared = sharedFonts(published, installedNames) ?: sharedFonts(added, installedNames)
      fonts = shared ?: TextFonts.forFontFamilies(installedNames, editorPreferences, fontRenderContext, specialCharacterFont)
    }
    return PublishedFontFamilies(value, installedNames, fonts)
  }

  private fun sharedFonts(entries: List<PublishedFontFamilies>, installedNames: List<String>): TextFonts? {
    return entries.firstOrNull { it.installedNames == installedNames }?.fonts
  }

  /**
   * A published value, the installed families of its names, in their order, and their fonts.
   */
  private class PublishedFontFamilies(
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
        published = listOf(),
      )
      return resolver.withFontFamilies(fontFamilies)
    }

    /**
     * Finds the published value. [FontFamilies] equality checks the identity first, so a lookup of the same object is fast.
     */
    private fun find(published: List<PublishedFontFamilies>, value: FontFamilies): PublishedFontFamilies? {
      for (entry in published) {
        if (entry.fontFamilies == value) {
          return entry
        }
      }
      return null
    }
  }
}
