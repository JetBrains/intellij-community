// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Font
import java.awt.font.TextAttribute

private const val EDITOR_SIZE = 13f
private const val CONSOLE_SIZE = 11f

@TestApplication
class EditorFontsTest {
  private val preferences: FontPreferences = FontPreferencesImpl().apply { register(Font.MONOSPACED, 13) }.snapshot

  @Test
  fun `the same settings give the same fonts`() {
    val fonts = fonts(useLigatures = null)

    assertThat(fonts.with(Font.MONOSPACED, EDITOR_SIZE, preferences, Font.MONOSPACED, CONSOLE_SIZE, preferences, null)).isSameAs(fonts)
  }

  @Test
  fun `another font size gives new fonts of that size`() {
    val fonts = fonts(useLigatures = null)

    val newFonts = fonts.with(Font.MONOSPACED, 20f, preferences, Font.MONOSPACED, CONSOLE_SIZE, preferences, null)

    assertThat(newFonts).isNotSameAs(fonts)
    assertThat(newFonts.get(EditorFontType.PLAIN).size2D).isEqualTo(20f)
    assertThat(newFonts.get(EditorFontType.CONSOLE_PLAIN).size2D).isEqualTo(CONSOLE_SIZE)
  }

  @Test
  fun `each font type gets its style and its size`() {
    val fonts = fonts(useLigatures = null)

    for (fontType in EditorFontType.entries) {
      val font = fonts.get(fontType)
      val console = fontType.name.startsWith("CONSOLE_")
      assertThat(font.size2D).describedAs(fontType.name).isEqualTo(if (console) CONSOLE_SIZE else EDITOR_SIZE)
      assertThat(font.isBold).describedAs(fontType.name).isEqualTo(fontType.name.contains("BOLD"))
      assertThat(font.isItalic).describedAs(fontType.name).isEqualTo(fontType.name.contains("ITALIC"))
    }
  }

  @Test
  fun `the ligatures override applies to all fonts`() {
    assertThat(preferences.useLigatures()).isFalse()

    val fonts = fonts(useLigatures = true)

    for (fontType in EditorFontType.entries) {
      assertThat(fonts.get(fontType).attributes[TextAttribute.LIGATURES]).describedAs(fontType.name).isEqualTo(TextAttribute.LIGATURES_ON)
    }
  }

  @Test
  fun `a size gives every font that size and keeps the last result`() {
    val fonts = fonts(useLigatures = null)

    val sized = fonts.withSize(30f)

    for (fontType in EditorFontType.entries) {
      assertThat(sized.get(fontType).size2D).describedAs(fontType.name).isEqualTo(30f)
      assertThat(sized.get(fontType).style).describedAs(fontType.name).isEqualTo(fonts.get(fontType).style)
    }
    assertThat(fonts.withSize(30f)).isSameAs(sized)
    assertThat(fonts.withSize(31f)).isNotSameAs(sized)
  }

  @Test
  fun `an empty set has no fonts`() {
    val empty = EditorFonts.empty()
    assertThat(empty.isEmpty()).isTrue()
    assertThrows<IllegalStateException> { empty.get(EditorFontType.PLAIN) }
    // the font cache tells its resets apart by the identity of the empty sets
    assertThat(EditorFonts.empty()).isNotSameAs(empty)
  }

  private fun fonts(useLigatures: Boolean?): EditorFonts {
    return EditorFonts.empty().with(Font.MONOSPACED, EDITOR_SIZE, preferences, Font.MONOSPACED, CONSOLE_SIZE, preferences, useLigatures)
  }
}
