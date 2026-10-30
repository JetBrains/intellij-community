// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.editor.impl.FontInfo
import java.awt.Font
import java.awt.font.FontRenderContext

/**
 * Turns the font settings of one editor into fonts and character widths.
 * The text layout and the soft wraps read the same resolver, so they measure with the same fonts.
 *
 * [EditorView] keeps one resolver in [EditorViewSnapshot] and creates it on the first use.
 * A settings change and a render context change make the snapshot drop it.
 * The resolver is safe to use on any thread.
 *
 * @param editorPreferences the font preferences of the editor
 * @param specialCharacterFont the font of the special character codes, or `null` when the editor does not show special characters
 */
internal class EditorFontResolver(
  editorPreferences: FontPreferences,
  private val fontRenderContext: FontRenderContext,
  private val specialCharacterFont: Font?,
) {
  private val editorFonts = TextFonts(editorPreferences.snapshot, fontRenderContext, specialCharacterFont)

  /**
   * Returns the fonts of text that has no font families of its own.
   */
  fun editorFonts(): TextFonts {
    return editorFonts
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

  companion object {
    /**
     * Marks a snapshot that has no resolver yet.
     */
    @JvmField
    val UNINITIALIZED: EditorFontResolver = EditorFontResolver(FontPreferencesImpl(), FontInfo.getFontRenderContext(null), null)
  }
}
