// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.util.ui.JdkConstants.FontStyle
import java.awt.Font
import java.awt.font.FontRenderContext

/**
 * Not thread-safe. [ComplementaryFontsRegistryImpl] calls it only under its lock.
 */
internal class FontFallback(private val baseFont: Font) {
  private val fontInfoMap = HashMap<FontKey, FontInfo>()

  /**
   * The key of the last call. [lastFontInfo] holds its result.
   * Both fields change together, and both are `null` before the first call.
   */
  private var lastFontKey: FontKey? = null
  private var lastFontInfo: FontInfo? = null

  constructor(familyName: String, @FontStyle style: Int) : this(Font(familyName, style, 1))

  fun baseFont(): Font {
    return baseFont
  }

  fun canDisplay(codePoint: Int, disableFontFallback: Boolean): Boolean {
    return codePoint < 128 || FontInfo.canDisplay(baseFont, codePoint, disableFontFallback)
  }

  fun getFontInfo(
    size: Float,
    useLigatures: Boolean,
    variants: Set<String>,
    fontRenderContext: FontRenderContext?,
  ): FontInfo {
    val lastKey = lastFontKey
    val lastInfo = lastFontInfo
    if (lastKey != null && lastInfo != null) {
      if (lastKey.matches(size, useLigatures, variants, fontRenderContext)) {
        return lastInfo
      }
    }
    val immutableVariants = java.util.Set.copyOf(variants)
    val key = FontKey(size, useLigatures, immutableVariants, fontRenderContext)
    val fontInfo = fontInfoMap.getOrPut(key) {
      FontInfo(baseFont, size, useLigatures, immutableVariants, fontRenderContext)
    }
    lastFontKey = key
    lastFontInfo = fontInfo
    return fontInfo
  }
}
