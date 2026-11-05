// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

private const val FIRST_KEY_NAME = "TextAttributesFontFamiliesTest.first"
private const val SECOND_KEY_NAME = "TextAttributesFontFamiliesTest.second"

/**
 * Checks that each kind of scheme gives the font families of its text attributes, see [EditorColorsScheme.getTextAttributesFontFamilies].
 */
@TestApplication
internal class TextAttributesFontFamiliesTest {
  private val first = TextAttributesKey.createTextAttributesKey(FIRST_KEY_NAME)
  private val second = TextAttributesKey.createTextAttributesKey(SECOND_KEY_NAME)
  private val serif = FontFamilies.of("Noto Serif")
  private val sans = FontFamilies.of("Noto Sans")

  @AfterEach
  fun tearDown() {
    TextAttributesKey.removeTextAttributesKey(FIRST_KEY_NAME)
    TextAttributesKey.removeTextAttributesKey(SECOND_KEY_NAME)
  }

  @Test
  fun `a scheme gives the font families of its own attributes and of its parent`() {
    val parent = EditorColorsSchemeImpl(null)
    parent.setAttributes(first, attributesWith(serif))
    val child = EditorColorsSchemeImpl(parent)
    child.setAttributes(second, attributesWith(sans))

    assertThat(child.textAttributesFontFamilies).containsExactlyInAnyOrder(serif, sans)
    assertThat(parent.textAttributesFontFamilies).containsExactly(serif)
  }

  @Test
  fun `a later change of the parent reaches the child`() {
    val parent = EditorColorsSchemeImpl(null)
    val child = EditorColorsSchemeImpl(parent)
    assertThat(child.textAttributesFontFamilies).isEmpty()

    parent.setAttributes(first, attributesWith(serif))

    assertThat(child.textAttributesFontFamilies).containsExactly(serif)
  }

  @Test
  fun `a delegate scheme gives the font families of its delegate`() {
    val delegate = EditorColorsSchemeImpl(null)
    delegate.setAttributes(first, attributesWith(serif))

    val scheme = object : DelegateColorScheme(delegate) {}

    assertThat(scheme.textAttributesFontFamilies).containsExactly(serif)
  }

  @Test
  fun `the scheme of an editor joins its own attributes with the font families of its delegate`() {
    runInEdtAndWait {
      val factory = EditorFactory.getInstance()
      val editor = factory.createEditor(factory.createDocument(""))
      try {
        val scheme = editor.colorsScheme
        val inherited = scheme.textAttributesFontFamilies

        scheme.setAttributes(first, attributesWith(serif))

        assertThat(scheme.textAttributesFontFamilies).containsAll(inherited).contains(serif)
      }
      finally {
        factory.releaseEditor(editor)
      }
    }
  }

  @Test
  fun `a scheme of another class gives the font families of the attributes of all keys`() {
    // The delegation does not cover the default methods of the interface, so the scheme keeps the default implementation.
    @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
    val scheme = object : EditorColorsScheme by EditorColorsSchemeImpl(null) {
      override fun getAttributes(key: TextAttributesKey?): TextAttributes? {
        return if (key == first) attributesWith(serif) else null
      }
    }

    assertThat(scheme.textAttributesFontFamilies).contains(serif).doesNotContain(sans)
  }

  private fun attributesWith(fontFamilies: FontFamilies): TextAttributes {
    val attributes = TextAttributes()
    attributes.fontFamilies = fontFamilies
    return attributes
  }
}
