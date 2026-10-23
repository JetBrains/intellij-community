// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.font.FontRenderContext

@TestApplication
@Suppress("SuspiciousPackagePrivateAccess")
internal class ComplementaryFontsRegistryImplTest {
  private val registry = ComplementaryFontsRegistryImpl(listOf(Font.MONOSPACED))
  private val preferences = FontPreferencesImpl().apply { register(Font.MONOSPACED, 13) }

  @Test
  fun `the same code point gives the same font info`() {
    val info = fontFor('a'.code)
    assertThat(info.size2D).isEqualTo(13f)
    assertThat(fontFor('a'.code)).isSameAs(info)
  }

  @Test
  fun `a character variant change gives a new font info`() {
    val plain = fontFor('a'.code)
    preferences.setCharacterVariant("zero", true)
    assertThat(fontFor('a'.code)).isNotSameAs(plain)
    preferences.setCharacterVariant("zero", false)
    assertThat(fontFor('a'.code)).isSameAs(plain)
  }

  @Test
  fun `a ligatures change gives a new font info`() {
    val plain = fontFor('a'.code)
    preferences.setUseLigatures(true)
    assertThat(fontFor('a'.code)).isNotSameAs(plain)
    preferences.setUseLigatures(false)
    assertThat(fontFor('a'.code)).isSameAs(plain)
  }

  @Test
  fun `a render context is part of the cache key`() {
    val info = fontFor('a'.code, context = FontRenderContext(null, true, true))
    assertThat(fontFor('a'.code, context = FontRenderContext(null, true, true))).isSameAs(info)
    assertThat(fontFor('a'.code, context = FontRenderContext(null, false, false))).isNotSameAs(info)
  }

  @Test
  fun `a code point that no font can display gives the placeholder font`() {
    val info = fontFor(INVALID_CODE_POINT)
    assertThat(info.font.name).isEqualTo(FontPreferences.JETBRAINS_MONO)
    assertThat(fontFor(INVALID_CODE_POINT)).isSameAs(info)
  }

  @Test
  fun `the placeholder font has the size of the first preferred family`() {
    val twoFamilies = FontPreferencesImpl().apply {
      register(Font.MONOSPACED, 17)
      register(Font.SERIF, 20)
    }
    assertThat(fontFor(INVALID_CODE_POINT, fontPreferences = twoFamilies).size2D).isEqualTo(17f)
  }

  @Test
  fun `preferences without font families give Monospaced in the default size`() {
    val info = fontFor('a'.code, fontPreferences = FontPreferencesImpl())
    assertThat(info.font.name).isEqualTo(Font.MONOSPACED)
    assertThat(info.size2D).isEqualTo(FontPreferences.DEFAULT_FONT_SIZE.toFloat())
  }

  @Test
  fun `a style out of range gives the plain style`() {
    val plain = fontFor('a'.code)
    assertThat(fontFor('a'.code, style = 42)).isSameAs(plain)
  }

  @Test
  fun `each style of a font family gets its own font`() {
    val plain = familyFontFor('a'.code, Font.MONOSPACED, style = Font.PLAIN)
    val bold = familyFontFor('a'.code, Font.MONOSPACED, style = Font.BOLD)
    val italic = familyFontFor('a'.code, Font.MONOSPACED, style = Font.ITALIC)
    val boldItalic = familyFontFor('a'.code, Font.MONOSPACED, style = Font.BOLD or Font.ITALIC)
    assertThat(listOf(plain, bold, italic, boldItalic)).doesNotHaveDuplicates()
    assertThat(plain.font.style).isEqualTo(Font.PLAIN)
    assertThat(bold.font.style).isEqualTo(Font.BOLD)
    assertThat(italic.font.style).isEqualTo(Font.ITALIC)
    assertThat(boldItalic.font.style).isEqualTo(Font.BOLD or Font.ITALIC)
  }

  @Test
  fun `a lookup by font family uses the given family and size`() {
    val info = familyFontFor('a'.code, Font.SERIF, size = 20)
    assertThat(info.font.name).isEqualTo(Font.SERIF)
    assertThat(info.size2D).isEqualTo(20f)
    assertThat(familyFontFor('a'.code, Font.SERIF, size = 20)).isSameAs(info)
  }

  @Test
  fun `a lookup by font family gives the placeholder font for a code point that no font can display`() {
    val info = familyFontFor(INVALID_CODE_POINT, Font.SERIF, size = 20)
    assertThat(info.font.name).isEqualTo(FontPreferences.JETBRAINS_MONO)
    assertThat(info.size2D).isEqualTo(20f)
  }

  @Test
  fun `a switch between font families keeps the font of each family`() {
    val monospaced = familyFontFor('a'.code, Font.MONOSPACED)
    val serif = familyFontFor('a'.code, Font.SERIF)
    assertThat(serif).isNotSameAs(monospaced)
    assertThat(familyFontFor('a'.code, Font.MONOSPACED)).isSameAs(monospaced)
    assertThat(familyFontFor('a'.code, Font.SERIF)).isSameAs(serif)
  }

  @Test
  fun `a lookup by plain family name does not reuse the last font of a preferred family`() {
    val preferred = fontFor('a'.code)
    val plain = familyFontFor('a'.code, Font.MONOSPACED)
    assertThat(plain).isNotSameAs(preferred)
    assertThat(fontFor('a'.code)).isSameAs(preferred)
  }

  @Test
  fun `text with one character gives the font of its code point`() {
    val info = fontFor('a'.code)
    assertThat(registry.getFontAbleToDisplay("xa", 1, 2, Font.PLAIN, preferences, null)).isSameAs(info)
    assertThat(registry.getFontAbleToDisplay(charArrayOf('x', 'a'), 1, 2, Font.PLAIN, preferences, null)).isSameAs(info)
  }

  @Test
  fun `a surrogate pair is one code point`() {
    val text = String(Character.toChars(GRINNING_FACE))
    val info = fontFor(GRINNING_FACE)
    assertThat(registry.getFontAbleToDisplay(text, 0, 2, Font.PLAIN, preferences, null)).isSameAs(info)
    assertThat(registry.getFontAbleToDisplay(text.toCharArray(), 0, 2, Font.PLAIN, preferences, null)).isSameAs(info)
  }

  @Test
  fun `text with more code points gets a font that displays all of them`() {
    val info = fontFor('a'.code)
    assertThat(registry.getFontAbleToDisplay("ab", 0, 2, Font.PLAIN, preferences, null)).isSameAs(info)
    assertThat(registry.getFontAbleToDisplay(charArrayOf('x', 'a', 'b'), 1, 3, Font.PLAIN, preferences, null)).isSameAs(info)
  }

  @Test
  fun `text that no font can display in full gives the placeholder font`() {
    val text = charArrayOf('a', Character.MIN_HIGH_SURROGATE)
    val info = registry.getFontAbleToDisplay(text, 0, 2, Font.PLAIN, preferences, null)
    assertThat(info.font.name).isEqualTo(FontPreferences.JETBRAINS_MONO)
  }

  @Test
  fun `concurrent lookups give one font info for each key`(): Unit = timeoutRunBlocking {
    val keys = listOf(Font.MONOSPACED, Font.SERIF).flatMap { family ->
      listOf(Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD or Font.ITALIC).flatMap { style ->
        listOf(12, 13, 14).map { size -> LookupKey(family, style, size) }
      }
    }
    val results = (0 until 8).map { worker ->
      async(Dispatchers.Default) {
        val found = HashMap<LookupKey, FontInfo>()
        repeat(200) { round ->
          for (i in keys.indices) {
            val key = keys[(i + worker + round) % keys.size]
            val info = familyFontFor('a'.code, key.family, key.style, key.size)
            assertThat(found.getOrPut(key) { info }).isSameAs(info)
          }
        }
        found
      }
    }.awaitAll()
    for (key in keys) {
      val expected = results.first().getValue(key)
      results.forEach { assertThat(it.getValue(key)).isSameAs(expected) }
    }
  }

  private fun fontFor(
    codePoint: Int,
    style: Int = Font.PLAIN,
    context: FontRenderContext? = null,
    fontPreferences: FontPreferences = preferences,
  ): FontInfo {
    return registry.getFontAbleToDisplay(codePoint, style, fontPreferences, context)
  }

  private fun familyFontFor(codePoint: Int, family: String, style: Int = Font.PLAIN, size: Int = 13): FontInfo {
    return registry.getFontAbleToDisplay(codePoint, size, style, family, null)
  }

  private data class LookupKey(val family: String, val style: Int, val size: Int)

  private companion object {
    const val INVALID_CODE_POINT = Character.MAX_CODE_POINT + 1
    const val GRINNING_FACE = 0x1F600
  }
}
