// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.testFramework.MockFontLayoutService
import java.awt.Font
import java.awt.FontMetrics

private const val OTHER_HEIGHT = 20
private const val OTHER_DESCENT = 4

/**
 * Gives the fonts of some font families their own character width, height and descent.
 * All other fonts get the width [otherWidth], the height 20 and the descent 4.
 * Each map takes the name of a font family as its key.
 *
 * @param familyWidths the character width of the fonts of each font family
 * @param familyHeights the height of the fonts of each font family
 * @param familyDescents the descent of the fonts of each font family
 */
internal class FamilyFontLayoutService(
  familyWidths: Map<String, Int>,
  private val otherWidth: Int,
  familyHeights: Map<String, Int> = mapOf(),
  familyDescents: Map<String, Int> = mapOf(),
) : MockFontLayoutService(otherWidth, OTHER_HEIGHT, OTHER_DESCENT) {
  private val widthsByFontFamily = byFontFamily(familyWidths)
  private val heightsByFontFamily = byFontFamily(familyHeights)
  private val descentsByFontFamily = byFontFamily(familyDescents)

  override fun charWidth2D(fontMetrics: FontMetrics, codePoint: Int): Float {
    val width = widthsByFontFamily[fontMetrics.font.family] ?: otherWidth
    return width.toFloat()
  }

  override fun getHeight(fontMetrics: FontMetrics): Int {
    return heightsByFontFamily[fontMetrics.font.family] ?: OTHER_HEIGHT
  }

  override fun getDescent(fontMetrics: FontMetrics): Int {
    return descentsByFontFamily[fontMetrics.font.family] ?: OTHER_DESCENT
  }
}

/**
 * Takes the keys of [values] from the names of the font families to the families of their AWT fonts, see [Font.getFamily].
 */
private fun byFontFamily(values: Map<String, Int>): Map<String, Int> {
  return values.mapKeys { FontFamilyService.getFont(it.key, null, null, Font.PLAIN).family }
}
