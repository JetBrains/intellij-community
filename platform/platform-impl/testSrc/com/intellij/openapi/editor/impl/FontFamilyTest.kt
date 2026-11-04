// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.awt.Font
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
@Suppress("SuspiciousPackagePrivateAccess")
internal class FontFamilyTest {
  @Test
  fun `the regular subfamily is the one closest to the weight 400`() {
    val test = TestFamily("Light" to 300, "Regular" to 400, "Bold" to 700)
    assertThat(test.family.recommendedSubFamily).isEqualTo("Regular")
    assertThat(test.family.getFont(null, null, Font.PLAIN)).isSameAs(test.font("Regular"))
  }

  @Test
  fun `of two subfamilies at the same distance from 400 the lighter one is regular`() {
    val test = TestFamily("Medium" to 500, "Light" to 300)
    assertThat(test.family.recommendedSubFamily).isEqualTo("Light")
  }

  @Test
  fun `the bold subfamily is the heavier one closest to the weight plus 300`() {
    val test = TestFamily("Regular" to 400, "SemiBold" to 600, "Bold" to 700, "Black" to 900)
    assertThat(test.family.getRecommendedBoldSubFamily("Regular")).isEqualTo("Bold")
    assertThat(test.family.getRecommendedBoldSubFamily("SemiBold")).isEqualTo("Black")
    assertThat(test.family.getRecommendedBoldSubFamily("Black")).isEqualTo("Black")
    assertThat(test.family.getFont(null, null, Font.BOLD)).isSameAs(test.font("Bold"))
    assertThat(test.family.getFont("SemiBold", null, Font.BOLD)).isSameAs(test.font("Black"))
  }

  @Test
  fun `an unknown main subfamily gives the bold subfamily of the regular one`() {
    val test = TestFamily("Thin" to 100, "Regular" to 400, "Bold" to 700)
    assertThat(test.family.getRecommendedBoldSubFamily("Thin")).isEqualTo("Regular")
    assertThat(test.family.getRecommendedBoldSubFamily("No Such")).isEqualTo("Bold")
    assertThat(test.family.getFont("No Such", null, Font.BOLD)).isSameAs(test.font("Bold"))
  }

  @Test
  fun `the plain subfamily of a bold subfamily is the lighter one closest to the weight minus 300`() {
    val test = TestFamily("Regular" to 400, "SemiBold" to 600, "Bold" to 700, "Black" to 900)
    assertThat(test.family.getFont(null, "Bold", Font.PLAIN)).isSameAs(test.font("Regular"))
    assertThat(test.family.getFont(null, "Black", Font.PLAIN)).isSameAs(test.font("SemiBold"))
    assertThat(test.family.getFont(null, "Black", Font.BOLD)).isSameAs(test.font("Black"))
  }

  @Test
  fun `without another weight, a subfamily of the same weight is the bold and the plain pair`() {
    val test = TestFamily("Book" to 400, "Regular" to 400)
    assertThat(test.family.getRecommendedBoldSubFamily("Book")).isEqualTo("Regular")
    assertThat(test.family.getRecommendedBoldSubFamily("Regular")).isEqualTo("Regular")
    assertThat(test.family.getFont(null, "Book", Font.PLAIN)).isSameAs(test.font("Regular"))
    assertThat(test.family.getFont(null, "Regular", Font.PLAIN)).isSameAs(test.font("Regular"))
  }

  @Test
  fun `getFont ignores an unknown subfamily`() {
    val test = TestFamily("Regular" to 400, "Bold" to 700)
    assertThat(test.family.getFont("No Such", "No Such", Font.PLAIN)).isSameAs(test.font("Regular"))
    assertThat(test.family.getFont("No Such", "No Such", Font.BOLD)).isSameAs(test.font("Bold"))
  }

  @Test
  fun `an italic font pairs with the base subfamily of its weight`() {
    val test = TestFamily("Regular" to 400, "Italic" to 400, "Bold" to 700, "Bold Italic" to 700)
    assertThat(test.family.getFont(null, null, Font.ITALIC)).isSameAs(test.font("Italic"))
    assertThat(test.family.getFont(null, null, Font.BOLD or Font.ITALIC)).isSameAs(test.font("Bold Italic"))
  }

  @Test
  fun `two italic fonts of one weight pair with the base subfamilies by name`() {
    val test = TestFamily("Book" to 400, "Regular" to 400, "Italic" to 400, "Book Italic" to 400)
    assertThat(test.family.getFont("Book", null, Font.ITALIC)).isSameAs(test.font("Book Italic"))
    assertThat(test.family.getFont("Regular", null, Font.ITALIC)).isSameAs(test.font("Italic"))
  }

  @Test
  fun `of two italic fonts that match the name of the base subfamily, the last one wins`() {
    val test = TestFamily("Book" to 400, "Book Italic" to 400, "Book Oblique" to 400, "Italic" to 400)
    assertThat(test.family.getFont("Book", null, Font.ITALIC)).isSameAs(test.font("Book Oblique"))
  }

  @Test
  fun `an italic font of another weight does not pair with the base subfamily`() {
    val test = TestFamily("Regular" to 400, "Bold Italic" to 700)
    val italic = test.family.getFont(null, null, Font.ITALIC)
    assertThat(italic.name).isEqualTo(test.font("Regular").name)
    assertThat(italic.isTransformed).isTrue()
  }

  @Test
  fun `a subfamily without an italic font gets a synthetic italic`() {
    val test = TestFamily("Regular" to 400, "Bold" to 700)
    val italic = test.family.getFont(null, null, Font.ITALIC)
    assertThat(italic.name).isEqualTo(test.font("Regular").name)
    assertThat(italic.isTransformed).isTrue()
    assertThat(italic.transform.shearX).isEqualTo(-0.2)
  }

  @Test
  fun `a family of italic fonts only uses them as base subfamilies`() {
    val test = TestFamily("Italic" to 400, "Bold Italic" to 700)
    assertThat(test.family.baseSubFamilies).containsExactly("Italic", "Bold Italic")
    assertThat(test.family.recommendedSubFamily).isEqualTo("Italic")
    assertThat(test.family.getFont(null, null, Font.ITALIC)).isSameAs(test.font("Italic"))
    assertThat(test.family.getFont(null, null, Font.BOLD)).isSameAs(test.font("Bold Italic"))
  }

  @Test
  fun `the base subfamilies are sorted by weight and leave out the italic fonts`() {
    val test = TestFamily("Bold" to 700, "Regular" to 400, "Light" to 300, "Italic" to 400)
    assertThat(test.family.baseSubFamilies).containsExactly("Light", "Regular", "Bold")
  }

  @Test
  fun `the base subfamilies are an immutable list`() {
    val test = TestFamily("Regular" to 400, "Bold" to 700)
    assertThatThrownBy { test.family.baseSubFamilies.add("Black") }.isInstanceOf(UnsupportedOperationException::class.java)
  }

  @Test
  fun `hasBaseSubFamily rejects an italic subfamily, getMember knows all fonts`() {
    val test = TestFamily("Regular" to 400, "Italic" to 400)
    assertThat(test.family.hasBaseSubFamily("Regular")).isTrue()
    assertThat(test.family.hasBaseSubFamily("Italic")).isFalse()
    assertThat(test.family.getMember("Italic")).isSameAs(test.font("Italic"))
    assertThat(test.family.getMember("No Such")).isNull()
  }

  @Test
  fun `a family needs at least one font`() {
    assertThatThrownBy { FontFamily("Test", mapOf()) { 400 } }.isInstanceOf(IllegalArgumentException::class.java)
  }

  @Test
  fun `the family keeps a copy of the members`() {
    val members = linkedMapOf("Regular" to Font("Test Regular", Font.PLAIN, 1))
    val family = FontFamily("Test", members) { 400 }
    members["Bold"] = Font("Test Bold", Font.PLAIN, 1)
    assertThat(family.getMember("Bold")).isNull()
    assertThat(family.baseSubFamilies).containsExactly("Regular")
  }

  @Test
  fun `the family reads each weight once, on the first use`() {
    val test = TestFamily("Regular" to 400, "Italic" to 400, "Bold" to 700)
    assertThat(test.weightReads.get()).isZero()
    test.family.getFont(null, null, Font.BOLD)
    test.family.baseSubFamilies
    test.family.getRecommendedBoldSubFamily("Regular")
    test.family.hasBaseSubFamily("Bold")
    assertThat(test.weightReads.get()).isEqualTo(3)
  }

  @Test
  fun `concurrent first uses read each weight once and give the same fonts`(): Unit = timeoutRunBlocking {
    val test = TestFamily("Light" to 300, "Regular" to 400, "Italic" to 400, "Bold" to 700, "Bold Italic" to 700)
    val styles = listOf(Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD or Font.ITALIC)
    val results = (0 until 8).map {
      async(Dispatchers.Default) {
        styles.map { style -> test.family.getFont(null, null, style) }
      }
    }.awaitAll()
    for (result in results) {
      assertThat(result).containsExactly(test.font("Regular"), test.font("Bold"), test.font("Italic"), test.font("Bold Italic"))
    }
    assertThat(test.weightReads.get()).isEqualTo(5)
  }

  @Test
  fun `a monospaced font makes a monospaced family`() {
    val family = FontFamily("Monospaced", mapOf("Regular" to Font(Font.MONOSPACED, Font.PLAIN, 1))) { 400 }
    assertThat(family.isMonospaced).isTrue()
  }

  @Test
  fun `a proportional font makes a proportional family`() {
    val family = FontFamily("Serif", mapOf("Regular" to Font(Font.SERIF, Font.PLAIN, 1))) { 400 }
    assertThat(family.isMonospaced).isFalse()
  }

  @Test
  fun `the family measures its monospacing once`() {
    val canDisplayCalls = AtomicInteger()
    val font = object : Font(MONOSPACED, PLAIN, 1) {
      override fun canDisplay(c: Char): Boolean {
        canDisplayCalls.incrementAndGet()
        return super.canDisplay(c)
      }
    }
    val family = FontFamily("Monospaced", mapOf("Regular" to font)) { 400 }
    assertThat(family.isMonospaced).isTrue()
    val callsOfOneMeasurement = canDisplayCalls.get()
    assertThat(callsOfOneMeasurement).isPositive()
    assertThat(family.isMonospaced).isTrue()
    assertThat(canDisplayCalls.get()).isEqualTo(callsOfOneMeasurement)
  }

  /**
   * A family of test fonts. Each font has a unique name, so the tests can tell the fonts apart.
   */
  private class TestFamily(vararg weights: Pair<String, Int>) {
    private val weightBySubFamily = mapOf(*weights)
    private val fonts = weightBySubFamily.keys.associateWith { Font("$FONT_NAME_PREFIX$it", Font.PLAIN, 1) }
    val weightReads = AtomicInteger()
    val family = FontFamily("Test", fonts) { font ->
      weightReads.incrementAndGet()
      weightBySubFamily.getValue(font.name.removePrefix(FONT_NAME_PREFIX))
    }

    fun font(subFamily: String): Font = fonts.getValue(subFamily)
  }

  private companion object {
    const val FONT_NAME_PREFIX = "Test "
  }
}
