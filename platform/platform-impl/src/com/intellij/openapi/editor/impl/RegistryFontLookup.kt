// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.util.ui.JdkConstants.FontStyle
import java.awt.font.FontRenderContext

/**
 * Finds the fonts in [ComplementaryFontsRegistry] with font preferences.
 * The lookup keeps a snapshot of the preferences, see [FontPreferences.getSnapshot].
 * Two lookups are equal when they have the same snapshot.
 */
internal class RegistryFontLookup(preferences: FontPreferences) : FontLookup {
  private val preferences: FontPreferences = preferences.snapshot

  override fun fontAbleToDisplay(
    text: CharArray,
    start: Int,
    end: Int,
    @FontStyle style: Int,
    context: FontRenderContext?,
  ): FontInfo {
    return ComplementaryFontsRegistry.getFontAbleToDisplay(text, start, end, style, preferences, context)
  }

  override fun fontAbleToDisplay(
    text: CharSequence,
    start: Int,
    end: Int,
    @FontStyle style: Int,
    context: FontRenderContext?,
  ): FontInfo {
    return ComplementaryFontsRegistry.getFontAbleToDisplay(text, start, end, style, preferences, context)
  }

  /**
   * Returns a font that can display [codePoint] in [style]. The returned font measures with [context].
   */
  fun fontAbleToDisplay(codePoint: Int, @FontStyle style: Int, context: FontRenderContext?): FontInfo {
    return ComplementaryFontsRegistry.getFontAbleToDisplay(codePoint, style, preferences, context)
  }

  override fun equals(other: Any?): Boolean {
    return this === other || other is RegistryFontLookup && preferences === other.preferences
  }

  override fun hashCode(): Int {
    return System.identityHashCode(preferences)
  }

  override fun toString(): String {
    return "RegistryFontLookup($preferences)"
  }
}
