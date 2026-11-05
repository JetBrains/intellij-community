// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.testFramework.MockFontLayoutService
import java.awt.Font
import java.awt.FontMetrics

/**
 * Gives the fonts of some font families their own character width, and all other fonts [otherWidth].
 *
 * @param familyWidths the character width of the fonts of each font family, by the name of the family
 */
internal class FamilyWidthFontLayoutService(
  familyWidths: Map<String, Int>,
  private val otherWidth: Int,
) : MockFontLayoutService(otherWidth, 20, 4) {
  /** The widths by the family of the AWT fonts, see [Font.getFamily]. */
  private val widthsByFontFamily: Map<String, Int> = familyWidths.mapKeys { FontFamilyService.getFont(it.key, null, null, Font.PLAIN).family }

  override fun charWidth2D(fontMetrics: FontMetrics, codePoint: Int): Float {
    val width = widthsByFontFamily[fontMetrics.font.family] ?: otherWidth
    return width.toFloat()
  }
}
