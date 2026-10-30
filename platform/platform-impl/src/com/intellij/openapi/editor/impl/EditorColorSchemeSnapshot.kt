// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.application.options.EditorFontsConstants
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.impl.EditorFonts
import com.intellij.openapi.editor.colors.impl.FontPreferencesSnapshot
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Color
import java.util.Collections

/**
 * An immutable snapshot of the state that [EditorColorSchemeDelegate] keeps for one editor.
 * The delegate keeps its current snapshot in one volatile field and replaces the snapshot on each change.
 * A reader that takes the snapshot once sees one consistent version on any thread.
 * A `with` function returns a new snapshot, or this snapshot when no value changes.
 *
 * A `null` font size, console font size, face name, line spacing or ligature setting means that the editor uses
 * the value of the delegate scheme.
 */
internal class EditorColorSchemeSnapshot private constructor(
  /** The editor font preferences. Without font families, the editor uses the preferences of the delegate scheme. */
  private val fontPreferences: FontPreferencesSnapshot,
  /** The console font preferences. Without font families, the editor uses the preferences of the delegate scheme. */
  private val consoleFontPreferences: FontPreferencesSnapshot,
  /** The attributes that the editor sets. A `null` value hides the attributes of the delegate scheme. */
  private val ownAttributes: Map<TextAttributesKey, TextAttributes?>,
  /** The colors that the editor sets. A `null` value hides the color of the delegate scheme. */
  private val ownColors: Map<ColorKey, Color?>,
  /** The fonts of the editor. Before the first font setting, the set is empty, and the editor uses the delegate scheme. */
  private val fonts: EditorFonts,
  private val maxFontSize: Float,
  private val fontSize: Float?,
  private val consoleFontSize: Float?,
  private val faceName: String?,
  private val lineSpacing: Float?,
  private val fontPreferencesAreSetExplicitly: Boolean,
  private val useLigatures: Boolean?,
) {
  fun fontPreferences(): FontPreferencesSnapshot = fontPreferences
  fun consoleFontPreferences(): FontPreferencesSnapshot = consoleFontPreferences
  fun ownAttributes(): Map<TextAttributesKey, TextAttributes?> = ownAttributes
  fun ownColors(): Map<ColorKey, Color?> = ownColors
  fun fonts(): EditorFonts = fonts
  fun maxFontSize(): Float = maxFontSize
  fun fontSize(): Float? = fontSize
  fun consoleFontSize(): Float? = consoleFontSize
  fun faceName(): String? = faceName
  fun lineSpacing(): Float? = lineSpacing
  fun fontPreferencesAreSetExplicitly(): Boolean = fontPreferencesAreSetExplicitly
  fun useLigatures(): Boolean? = useLigatures

  fun withFontPreferences(fontPreferences: FontPreferencesSnapshot): EditorColorSchemeSnapshot {
    return if (fontPreferences === this.fontPreferences) this else copy(fontPreferences = fontPreferences)
  }

  fun withConsoleFontPreferences(consoleFontPreferences: FontPreferencesSnapshot): EditorColorSchemeSnapshot {
    return if (consoleFontPreferences === this.consoleFontPreferences) this else copy(consoleFontPreferences = consoleFontPreferences)
  }

  fun withOwnAttributes(key: TextAttributesKey, attributes: TextAttributes?): EditorColorSchemeSnapshot {
    if (ownAttributes.containsKey(key) && ownAttributes[key] === attributes) {
      return this
    }
    val newAttributes = HashMap(ownAttributes)
    newAttributes[key] = attributes
    return copy(ownAttributes = Collections.unmodifiableMap(newAttributes))
  }

  fun withOwnColor(key: ColorKey, color: Color?): EditorColorSchemeSnapshot {
    if (ownColors.containsKey(key) && ownColors[key] === color) {
      return this
    }
    val newColors = HashMap(ownColors)
    newColors[key] = color
    return copy(ownColors = Collections.unmodifiableMap(newColors))
  }

  fun withoutOwnColor(key: ColorKey): EditorColorSchemeSnapshot {
    if (!ownColors.containsKey(key)) {
      return this
    }
    val newColors = HashMap(ownColors)
    newColors.remove(key)
    return copy(ownColors = Collections.unmodifiableMap(newColors))
  }

  fun withFonts(fonts: EditorFonts): EditorColorSchemeSnapshot {
    return if (fonts === this.fonts) this else copy(fonts = fonts)
  }

  fun withMaxFontSize(maxFontSize: Float): EditorColorSchemeSnapshot {
    return if (maxFontSize == this.maxFontSize) this else copy(maxFontSize = maxFontSize)
  }

  fun withFontSize(fontSize: Float?): EditorColorSchemeSnapshot {
    return if (fontSize == this.fontSize) this else copy(fontSize = fontSize)
  }

  fun withConsoleFontSize(consoleFontSize: Float?): EditorColorSchemeSnapshot {
    return if (consoleFontSize == this.consoleFontSize) this else copy(consoleFontSize = consoleFontSize)
  }

  fun withFaceName(faceName: String?): EditorColorSchemeSnapshot {
    return if (faceName == this.faceName) this else copy(faceName = faceName)
  }

  fun withLineSpacing(lineSpacing: Float?): EditorColorSchemeSnapshot {
    return if (lineSpacing == this.lineSpacing) this else copy(lineSpacing = lineSpacing)
  }

  fun withFontPreferencesSetExplicitly(fontPreferencesAreSetExplicitly: Boolean): EditorColorSchemeSnapshot {
    return if (fontPreferencesAreSetExplicitly == this.fontPreferencesAreSetExplicitly) {
      this
    } else {
      copy(fontPreferencesAreSetExplicitly = fontPreferencesAreSetExplicitly)
    }
  }

  fun withUseLigatures(useLigatures: Boolean?): EditorColorSchemeSnapshot {
    return if (useLigatures == this.useLigatures) this else copy(useLigatures = useLigatures)
  }

  private fun copy(
    fontPreferences: FontPreferencesSnapshot = this.fontPreferences,
    consoleFontPreferences: FontPreferencesSnapshot = this.consoleFontPreferences,
    ownAttributes: Map<TextAttributesKey, TextAttributes?> = this.ownAttributes,
    ownColors: Map<ColorKey, Color?> = this.ownColors,
    fonts: EditorFonts = this.fonts,
    maxFontSize: Float = this.maxFontSize,
    fontSize: Float? = this.fontSize,
    consoleFontSize: Float? = this.consoleFontSize,
    faceName: String? = this.faceName,
    lineSpacing: Float? = this.lineSpacing,
    fontPreferencesAreSetExplicitly: Boolean = this.fontPreferencesAreSetExplicitly,
    useLigatures: Boolean? = this.useLigatures,
  ): EditorColorSchemeSnapshot {
    return EditorColorSchemeSnapshot(
      fontPreferences = fontPreferences,
      consoleFontPreferences = consoleFontPreferences,
      ownAttributes = ownAttributes,
      ownColors = ownColors,
      fonts = fonts,
      maxFontSize = maxFontSize,
      fontSize = fontSize,
      consoleFontSize = consoleFontSize,
      faceName = faceName,
      lineSpacing = lineSpacing,
      fontPreferencesAreSetExplicitly = fontPreferencesAreSetExplicitly,
      useLigatures = useLigatures,
    )
  }

  companion object {
    /**
     * Returns the snapshot of an editor that sets nothing of its own.
     */
    @JvmStatic
    fun initial(): EditorColorSchemeSnapshot {
      return EditorColorSchemeSnapshot(
        fontPreferences = FontPreferencesSnapshot.EMPTY,
        consoleFontPreferences = FontPreferencesSnapshot.EMPTY,
        ownAttributes = emptyMap(),
        ownColors = emptyMap(),
        fonts = EditorFonts.empty(),
        maxFontSize = EditorFontsConstants.getMaxEditorFontSize().toFloat(),
        fontSize = null,
        consoleFontSize = null,
        faceName = null,
        lineSpacing = null,
        fontPreferencesAreSetExplicitly = false,
        useLigatures = null,
      )
    }
  }
}
