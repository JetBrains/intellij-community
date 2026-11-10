// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SuspiciousPackagePrivateAccess")

package com.intellij.application.options.colors

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme
import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.AbstractFontCombo
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.Icon

private val KEYWORD: TextAttributesKey = DefaultLanguageHighlighterColors.KEYWORD

/**
 * Two installed families that [FontFamilies.of] accepts.
 */
private val installedFamilies: List<String> by lazy {
  FontFamilyService.getAvailableFamilies().asSequence().filter { !it.startsWith(".") && ',' !in it }.take(2).toList()
}

/**
 * Checks that the font row of [ColorAndFontDescriptionPanel] changes the edited scheme of [ColorAndFontOptions].
 * A test page holds the keyword key, so the test does not depend on the pages of the plugins.
 * Each step applies the panel and then resets it, as [OptionsPanelImpl] does after each change.
 */
@TestApplication
internal class ColorAndFontOptionsFontFamiliesTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val first = installedFamilies[0]
  private val second = installedFamilies[1]

  @BeforeEach
  fun maskPages() {
    ExtensionTestUtil.maskExtensions(ColorSettingsPage.EP_NAME, listOf(KeywordPage()), disposable)
    ExtensionTestUtil.maskExtensions(ColorSettingsPageEP.EP_NAME, emptyList(), disposable)
    ExtensionTestUtil.maskExtensions(ColorAndFontPanelFactory.EP_NAME, emptyList(), disposable)
  }

  @Test
  fun `each selected family reaches the font families of the edited scheme`(): Unit = withKeyword { options, panel, description ->
    val scheme = options.selectedScheme

    select(panel, description, scheme, first)
    assertThat(scheme.getAttributes(KEYWORD).fontFamilies).isEqualTo(FontFamilies.of(first))
    assertThat(scheme.textAttributesFontFamilies).contains(FontFamilies.of(first))

    select(panel, description, scheme, second)
    assertThat(scheme.getAttributes(KEYWORD).fontFamilies).isEqualTo(FontFamilies.of(second))
    assertThat(scheme.textAttributesFontFamilies).contains(FontFamilies.of(second))
  }

  @Test
  fun `the option marks a font family change and reverts it`(): Unit = withKeyword { options, panel, description ->
    select(panel, description, options.selectedScheme, first)
    assertThat(description.isModified).isTrue()
    assertThat(description.isChannelModified(ColorAndFontDescription.Channel.FONT_FAMILIES)).isTrue()

    description.revertChannel(ColorAndFontDescription.Channel.FONT_FAMILIES)

    assertThat(description.externalFontFamilies).isNull()
    assertThat(description.isChannelModified(ColorAndFontDescription.Channel.FONT_FAMILIES)).isFalse()
  }

  @Test
  fun `the written scheme has the font family`(): Unit = withKeyword { options, panel, description ->
    val scheme = options.selectedScheme
    select(panel, description, scheme, first)

    val element = Element("scheme")
    (scheme as AbstractColorsScheme).writeExternal(element)

    assertThat(findOption(element, "FONT_FAMILIES")?.getAttributeValue("value")).isEqualTo(first)
  }

  private fun withKeyword(test: (ColorAndFontOptions, ColorAndFontDescriptionPanel, ColorAndFontDescription) -> Unit) {
    runInEdtAndWait {
      val options = ColorAndFontOptions()
      try {
        val description = options.currentDescriptions
          .filterIsInstance<ColorAndFontDescription>()
          .single { it.type == KEYWORD.externalName }
        val panel = ColorAndFontDescriptionPanel()
        panel.reset(description)
        test(options, panel, description)
      }
      finally {
        options.disposeUIResources()
      }
    }
  }
}

/**
 * Selects the family in the font row, and then applies and resets the panel.
 */
private fun select(panel: ColorAndFontDescriptionPanel, description: ColorAndFontDescription, scheme: EditorColorsScheme, family: String) {
  UIUtil.findComponentOfType(panel, AbstractFontCombo::class.java)!!.fontName = family
  panel.apply(description, scheme)
  panel.reset(description)
}

private fun findOption(element: Element, name: String): Element? {
  if (element.name == "option" && element.getAttributeValue("name") == name) {
    return element
  }
  for (child in element.children) {
    val found = findOption(child, name)
    if (found != null) {
      return found
    }
  }
  return null
}

private class KeywordPage : ColorSettingsPage {
  override fun getIcon(): Icon? = null

  override fun getHighlighter(): SyntaxHighlighter = PlainSyntaxHighlighter()

  override fun getDemoText(): String = ""

  override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

  override fun getAttributeDescriptors(): Array<AttributesDescriptor> = arrayOf(AttributesDescriptor("Keyword", KEYWORD))

  override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

  override fun getDisplayName(): String = "Keywords"

  override fun getId(): String = "font-families-keywords"
}
