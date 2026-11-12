// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor.fonts

import com.intellij.application.options.colors.AbstractFontOptionsPanel
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.UI
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.ModifiableFontPreferences
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField

/**
 * Checks the letter spacing field of the font settings pages.
 * The test finds the field by its label, as a user does.
 */
@TestApplication
internal class AppFontOptionsPanelLetterSpacingTest {
  @Test
  fun `the field writes the letter spacing to the font preferences`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.UI) {
      val scheme = createPreviewScheme()
      val field = letterSpacingField(AppEditorFontOptionsPanel(scheme))
      field.text = "0.05"
      assertThat(scheme.fontPreferences.letterSpacing).isEqualTo(0.05f)
    }
  }

  @Test
  fun `a wrong value changes nothing and shows the error with the range`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.UI) {
      val scheme = createPreviewScheme()
      val field = letterSpacingField(AppEditorFontOptionsPanel(scheme))
      field.text = "0.05"
      for (wrong in listOf("5", "-0.6", "not a number", "")) {
        field.text = wrong
        assertThat(scheme.fontPreferences.letterSpacing).isEqualTo(0.05f)
        assertThat(field.getClientProperty("JComponent.outline")).isEqualTo("error")
        assertThat(field.toolTipText).contains("-0.50", "1.00")
      }
      field.text = "-0.01"
      assertThat(scheme.fontPreferences.letterSpacing).isEqualTo(-0.01f)
      assertThat(field.getClientProperty("JComponent.outline")).isNull()
      assertThat(field.toolTipText).isNull()
    }
  }

  @Test
  fun `the field shows two decimals, or three when the third is not 0`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.UI) {
      val scheme = createPreviewScheme()
      val panel = AppEditorFontOptionsPanel(scheme)
      val preferences = scheme.fontPreferences as ModifiableFontPreferences
      for (case in listOf(0f to "0.00", -0f to "0.00", -0.01f to "-0.01", 0.025f to "0.025", 1f to "1.00")) {
        preferences.letterSpacing = case.first
        panel.updateOptionsList()
        assertThat(letterSpacingField(panel).text).isEqualTo(case.second)
      }
    }
  }

  @Test
  fun `the field shows the letter spacing of the font preferences`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.UI) {
      val scheme = createPreviewScheme()
      val panel = AppEditorFontOptionsPanel(scheme)
      (scheme.fontPreferences as ModifiableFontPreferences).letterSpacing = 0.1f
      panel.updateOptionsList()
      assertThat(letterSpacingField(panel).text).isEqualTo("0.10")
      assertThat(scheme.fontPreferences.letterSpacing).isEqualTo(0.1f)
    }
  }

  @Test
  fun `the application font pages show the field`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.UI) {
      assertThat(letterSpacingLabels(AppEditorFontOptionsPanel(createPreviewScheme()))).hasSize(1)
      assertThat(letterSpacingLabels(AppFontOptionsPanel(createPreviewScheme()))).hasSize(1)
    }
  }

  @Test
  fun `the scheme font layout shows the field only when the page saves the letter spacing`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.UI) {
      val preferences = FontPreferencesImpl()
      assertThat(letterSpacingLabels(TestFontOptionsPanel(preferences))).isEmpty()
      val panel = LetterSpacingFontOptionsPanel(preferences)
      letterSpacingField(panel).text = "0.05"
      assertThat(preferences.letterSpacing).isEqualTo(0.05f)
    }
  }

  /**
   * Creates the scheme of the page preview, as [AppFontPanel] does.
   */
  private fun createPreviewScheme(): EditorColorsScheme {
    val scheme = EditorColorsManager.getInstance().schemeForCurrentUITheme.clone() as EditorColorsScheme
    scheme.fontPreferences = FontPreferencesImpl()
    return scheme
  }

  /**
   * A page with the layout of the color scheme font pages, see [AbstractFontOptionsPanel.createFontSettingsPanel].
   * It does not save the letter spacing.
   */
  private open class TestFontOptionsPanel(private val preferences: FontPreferences) : AbstractFontOptionsPanel() {
    override fun isReadOnly(): Boolean = false

    override fun isDelegating(): Boolean = false

    override fun getFontPreferences(): FontPreferences = preferences

    override fun setFontSize(fontSize: Int) {
    }

    override fun getLineSpacing(): Float = preferences.lineSpacing

    override fun setCurrentLineSpacing(lineSpacing: Float) {
    }
  }

  /**
   * A page with the layout of the color scheme font pages that saves the letter spacing.
   * The constructor of the superclass reads [isLetterSpacingSupported], so the value is a constant.
   */
  private class LetterSpacingFontOptionsPanel(preferences: FontPreferences) : TestFontOptionsPanel(preferences) {
    override fun isLetterSpacingSupported(): Boolean = true
  }

  private fun letterSpacingLabels(panel: JComponent): List<JLabel> {
    val text = ApplicationBundle.message("editbox.letter.spacing")
    return UIUtil.findComponentsOfType(panel, JLabel::class.java).filter { it.text == text }
  }

  private fun letterSpacingField(panel: JComponent): JTextField {
    return letterSpacingLabels(panel).single().labelFor as JTextField
  }
}
