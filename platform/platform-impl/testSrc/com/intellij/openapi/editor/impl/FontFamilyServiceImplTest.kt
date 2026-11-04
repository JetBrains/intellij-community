// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.GraphicsEnvironment

/**
 * Runs the font scan of [FontFamilyServiceImpl] on the fonts of the test machine.
 * The service skips the scan in unit test mode, so the test calls [FontFamilyServiceImpl.loadFamilies].
 * The machine decides which fonts exist, so the test checks only rules that hold for any set of fonts.
 */
@TestApplication
@Suppress("SuspiciousPackagePrivateAccess")
internal class FontFamilyServiceImplTest {
  private lateinit var families: Map<String, FontFamily>

  @BeforeEach
  fun scanFonts() {
    assumeTrue(FontFamilyServiceImpl.RUNTIME_API_AVAILABLE, "The runtime gives no typographic font names")
    val fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts
    assumeTrue(fonts.isNotEmpty(), "The test machine has no fonts")
    families = FontFamilyServiceImpl.loadFamilies(fonts)
  }

  @Test
  fun `the scan finds font families`() {
    assertThat(families).isNotEmpty()
  }

  @Test
  fun `each family of the scan has its recommended font for each style`() {
    for (name in families.keys) {
      val family = families.getValue(name)
      assertThat(family.baseSubFamilies).describedAs(name).isNotEmpty()
      assertThat(family.getFont(null, null, Font.PLAIN)).describedAs(name).isSameAs(family.getMember(family.recommendedSubFamily))
      for (style in listOf(Font.BOLD, Font.ITALIC, Font.BOLD or Font.ITALIC)) {
        assertThat(family.getFont(null, null, style)).describedAs("$name, style $style").isNotNull()
      }
    }
  }

  @Test
  fun `the families of the scan are an immutable map`() {
    assertThatThrownBy { (families as MutableMap<String, FontFamily>).clear() }.isInstanceOf(UnsupportedOperationException::class.java)
  }
}
