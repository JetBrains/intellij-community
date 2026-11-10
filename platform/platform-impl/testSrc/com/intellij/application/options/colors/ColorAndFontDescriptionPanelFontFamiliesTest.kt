// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SuspiciousPackagePrivateAccess")

package com.intellij.application.options.colors

import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.AbstractFontCombo
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JLabel

private const val MISSING_FAMILY = "No Such Font Family"

/**
 * Two installed families that [FontFamilies.of] accepts.
 */
private val installedFamilies: List<String> by lazy {
  FontFamilyService.getAvailableFamilies().asSequence().filter { !it.startsWith(".") && ',' !in it }.take(2).toList()
}

/**
 * Checks the font row of [ColorAndFontDescriptionPanel]: what [ColorAndFontDescriptionPanel.apply] writes into the option.
 * Each step applies the panel and then resets it, as [OptionsPanelImpl] does after each change.
 */
@TestApplication
internal class ColorAndFontDescriptionPanelFontFamiliesTest {
  private val first = installedFamilies[0]
  private val second = installedFamilies[1]

  @Test
  fun `an option without font families shows None`(): Unit = withPanel(null) { panel, description ->
    assertThat(fontFamilyCombo(panel).isNoFontSelected).isTrue()

    applyAndReset(panel, description)

    assertThat(description.externalFontFamilies).isNull()
    assertThat(fontFamilyCombo(panel).isNoFontSelected).isTrue()
  }

  @Test
  fun `a selected family becomes the font families of the option`(): Unit = withPanel(null) { panel, description ->
    fontFamilyCombo(panel).fontName = first

    applyAndReset(panel, description)

    assertThat(description.externalFontFamilies).isEqualTo(FontFamilies.of(first))
    assertThat(fontFamilyCombo(panel).fontName).isEqualTo(first)
  }

  @Test
  fun `None removes the font families`(): Unit = withPanel(FontFamilies.of(first)) { panel, description ->
    fontFamilyCombo(panel).fontName = null

    applyAndReset(panel, description)

    assertThat(description.externalFontFamilies).isNull()
    assertThat(fontFamilyCombo(panel).isNoFontSelected).isTrue()
  }

  @Test
  fun `a list of font families stays until the user selects another family`(): Unit =
    withPanel(FontFamilies.of(first, second)) { panel, description ->
      assertThat(fontFamilyCombo(panel).fontName).isEqualTo(first)

      applyAndReset(panel, description)
      assertThat(description.externalFontFamilies).isEqualTo(FontFamilies.of(first, second))

      fontFamilyCombo(panel).fontName = second
      applyAndReset(panel, description)
      assertThat(description.externalFontFamilies).isEqualTo(FontFamilies.of(second))
    }

  @Test
  fun `a font family that is not installed stays`(): Unit = withPanel(FontFamilies.of(MISSING_FAMILY)) { panel, description ->
    applyAndReset(panel, description)
    assertThat(description.externalFontFamilies).isEqualTo(FontFamilies.of(MISSING_FAMILY))

    applyAndReset(panel, description)
    assertThat(description.externalFontFamilies).isEqualTo(FontFamilies.of(MISSING_FAMILY))
  }

  @Test
  fun `an option without the font row keeps its font families`() {
    runInEdtAndWait {
      val panel = ColorAndFontDescriptionPanel()
      val description = PlainDescription(attributesWith(FontFamilies.of(first)), editableScheme())
      panel.reset(description)

      assertThat(fontLabel(panel).isVisible).isFalse()
      assertThat(UIUtil.findComponentOfType(panel, AbstractFontCombo::class.java)).isNull()

      applyAndReset(panel, description)

      assertThat(description.externalFontFamilies).isEqualTo(FontFamilies.of(first))
    }
  }

  private fun withPanel(fontFamilies: FontFamilies?, test: (ColorAndFontDescriptionPanel, ColorAndFontDescription) -> Unit) {
    runInEdtAndWait {
      val panel = ColorAndFontDescriptionPanel()
      val description = FontFamiliesDescription(attributesWith(fontFamilies), editableScheme())
      panel.reset(description)
      assertThat(fontLabel(panel).isVisible).isTrue()
      test(panel, description)
    }
  }
}

/**
 * An option that shows the font row, as an option of a color scheme page does.
 */
private class FontFamiliesDescription(attributes: TextAttributes, scheme: EditorColorsScheme) :
  TextAttributesDescription("Keyword", null, attributes, null, scheme, null, null) {
  override fun isFontFamiliesEnabled(): Boolean {
    return true
  }
}

/**
 * An option without the font row, as an option of the severity editor is.
 */
private class PlainDescription(attributes: TextAttributes, scheme: EditorColorsScheme) :
  TextAttributesDescription("Keyword", null, attributes, null, scheme, null, null)

private fun applyAndReset(panel: ColorAndFontDescriptionPanel, description: ColorAndFontDescription) {
  panel.apply(description, null)
  panel.reset(description)
}

private fun attributesWith(fontFamilies: FontFamilies?): TextAttributes {
  val attributes = TextAttributes()
  attributes.fontFamilies = fontFamilies
  return attributes
}

private fun editableScheme(): EditorColorsScheme {
  return EditorColorsSchemeImpl(EditorColorsManager.getInstance().globalScheme)
}

private fun fontLabel(panel: ColorAndFontDescriptionPanel): JLabel {
  val text = ApplicationBundle.message("primary.font")
  return UIUtil.findComponentsOfType(panel, JLabel::class.java).single { it.text == text }
}

private fun fontFamilyCombo(panel: ColorAndFontDescriptionPanel): AbstractFontCombo<*> {
  return UIUtil.findComponentOfType(panel, AbstractFontCombo::class.java)!!
}
