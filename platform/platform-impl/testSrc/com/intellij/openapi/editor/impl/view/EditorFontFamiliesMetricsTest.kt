// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val FAMILY_WIDTH = 30
private const val FAMILY_HEIGHT = 40
private const val FAMILY_DESCENT = 10
private const val OTHER_WIDTH = 10
private const val MISSING_FAMILY = "No Such Font Family"

/**
 * Checks that the overhangs and the width estimates of the editor cover the resolved font families.
 * [FamilyFontLayoutService] gives the fonts of one font family a larger width, height and descent than the editor fonts.
 */
@TestApplication
@Suppress("SuspiciousPackagePrivateAccess")
internal class EditorFontFamiliesMetricsTest {
  private val family = FontFamilyService.getAvailableFamilies().first { !it.startsWith(".") }
  private val value = FontFamilies.of(family)

  @BeforeEach
  fun setUp() {
    val layoutService = FamilyFontLayoutService(
      familyWidths = mapOf(family to FAMILY_WIDTH),
      otherWidth = OTHER_WIDTH,
      familyHeights = mapOf(family to FAMILY_HEIGHT),
      familyDescents = mapOf(family to FAMILY_DESCENT),
    )
    FontLayoutService.setInstance(layoutService)
  }

  @AfterEach
  fun tearDown() {
    FontLayoutService.setInstance(null)
  }

  @Test
  fun `a tall resolved font family widens the overhangs`(): Unit = withEditor { editor ->
    val view = editor.view

    view.resolveFontFamilies(listOf(value))

    assertThat(view.topOverhang).isEqualTo(FAMILY_HEIGHT - FAMILY_DESCENT - view.ascent)
    assertThat(view.bottomOverhang).isEqualTo(FAMILY_DESCENT - view.descent)
  }

  @Test
  fun `a value without installed families keeps the overhangs of the editor fonts`(): Unit = withEditor { editor ->
    val view = editor.view
    val topOverhang = view.topOverhang
    val bottomOverhang = view.bottomOverhang

    view.resolveFontFamilies(listOf(FontFamilies.of(MISSING_FAMILY)))

    assertThat(view.topOverhang).isEqualTo(topOverhang).isLessThan(FAMILY_HEIGHT - FAMILY_DESCENT - view.ascent)
    assertThat(view.bottomOverhang).isEqualTo(bottomOverhang).isLessThan(FAMILY_DESCENT - view.descent)
  }

  @Test
  fun `a reinit keeps the overhangs of the resolved font families`(): Unit = withEditor { editor ->
    val view = editor.view
    view.resolveFontFamilies(listOf(value))

    editor.reinitSettings()

    assertThat(view.topOverhang).isEqualTo(FAMILY_HEIGHT - FAMILY_DESCENT - view.ascent)
    assertThat(view.bottomOverhang).isEqualTo(FAMILY_DESCENT - view.descent)
  }

  @Test
  fun `a wide resolved font family raises the width estimate and keeps the cells of the character grid`(): Unit = withEditor { editor ->
    val view = editor.view
    editor.settings.characterGridWidthMultiplier = 1f
    val cellWidth = editor.characterGrid!!.charWidth

    view.resolveFontFamilies(listOf(value))

    assertThat(view.maxCharWidth).isEqualTo(FAMILY_WIDTH.toFloat())
    assertThat(editor.characterGrid!!.charWidth).isEqualTo(cellWidth).isEqualTo(OTHER_WIDTH.toFloat())
  }

  private fun withEditor(test: (EditorImpl) -> Unit) {
    runInEdtAndWait {
      val factory = EditorFactory.getInstance()
      val editor = factory.createEditor(factory.createDocument("aaaa")) as EditorImpl
      try {
        test(editor)
      }
      finally {
        factory.releaseEditor(editor)
      }
    }
  }
}
