// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.image.BufferedImage
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val CHAR_WIDTH = 10
private const val NON_BREAKING_SPACE = 0xA0.toChar()
private const val MISSING_FAMILY = "No Such Font Family"
private const val PUBLISHING_THREADS = 8
private const val PUBLISHING_ROUNDS = 5

/**
 * Checks when [EditorView] creates a new [EditorFontResolver], that the layout and the soft wraps use its fonts,
 * and how the view publishes font families.
 */
@TestApplication
@Suppress("SuspiciousPackagePrivateAccess")
internal class EditorFontResolverTest {
  @Test
  fun `the resolver stays until the settings change`(): Unit = withEditor { editor ->
    val resolver = editor.view.fontResolver
    assertThat(editor.view.fontResolver).isSameAs(resolver)

    editor.reinitSettings()

    assertThat(editor.view.fontResolver).isNotSameAs(resolver)
  }

  @Test
  fun `a scheme setter without a change keeps the resolver`(): Unit = withEditor { editor ->
    editor.colorsScheme.isUseLigatures = true
    val resolver = editor.view.fontResolver

    editor.colorsScheme.isUseLigatures = true

    assertThat(editor.view.fontResolver).isSameAs(resolver)
  }

  @Test
  fun `the editor fonts are the fonts of the font preferences of the editor`(): Unit = withEditor { editor ->
    val resolver = editor.view.fontResolver
    val context = resolver.fontRenderContext()
    for (text in listOf("a", "\u0436", "\u6211")) {
      val chars = text.toCharArray()
      val expected = ComplementaryFontsRegistry.getFontAbleToDisplay(chars, 0, chars.size, Font.BOLD, editor.colorsScheme.fontPreferences, context)
      assertThat(resolver.editorFonts().fontAbleToDisplay(chars, 0, chars.size, Font.BOLD, context)).describedAs(text).isSameAs(expected)
    }
  }

  @Test
  fun `the resolver has the special character font only when the editor shows special characters`(): Unit = withEditor { editor ->
    editor.settings.isShowingSpecialChars = false
    assertThat(editor.view.fontResolver.specialCharacterFont()).isNull()

    editor.settings.isShowingSpecialChars = true

    assertThat(editor.view.fontResolver.specialCharacterFont()).isEqualTo(editor.colorsScheme.getFont(EditorFontType.PLAIN))
  }

  @Test
  fun `the layout and the soft wraps give a special character the width of its code`() {
    FontLayoutService.setInstance(MockFontLayoutService(CHAR_WIDTH, CHAR_WIDTH, 2))
    try {
      withEditor("a" + NON_BREAKING_SPACE + "b") { editor ->
        editor.settings.isShowingSpecialChars = true
        // the code is "NBSP"
        assertThat(editor.offsetToXY(2).x - editor.offsetToXY(1).x).isEqualTo(4 * CHAR_WIDTH)

        EditorTestUtil.configureSoftWraps(editor, 4)

        // the line is 6 characters wide with the code, and fits in 4 columns only without the code
        assertThat(editor.softWrapModel.registeredSoftWraps).isNotEmpty()
      }
    }
    finally {
      FontLayoutService.setInstance(null)
    }
  }

  @Test
  fun `a render context change gives a new resolver`(): Unit = withEditor { editor ->
    val resolver = editor.view.fontResolver

    paintScaled(editor)

    val newResolver = editor.view.fontResolver
    assertThat(newResolver).isNotSameAs(resolver)
    assertThat(newResolver.fontRenderContext().transform.scaleX).isEqualTo(2.0)
  }

  @Test
  fun `a published value gets the fonts of its families`(): Unit = withEditor { editor ->
    val family = installedFamilies().first()
    val value = FontFamilies.of(family)
    assertThat(editor.view.fontResolver.isPublished(value)).isFalse()
    assertThat(editor.view.fontResolver.fonts(value)).isSameAs(editor.view.fontResolver.editorFonts())

    assertThat(editor.view.publishFontFamilies(listOf(value))).isTrue()

    val resolver = editor.view.fontResolver
    val fonts = resolver.fonts(value)
    assertThat(resolver.isPublished(value)).isTrue()
    assertThat(fonts).isNotSameAs(resolver.editorFonts())
    val font = fonts.fontAbleToDisplay("a", 0, 1, Font.PLAIN, resolver.fontRenderContext())
    val familyFont = FontFamilyService.getFont(family, null, null, Font.PLAIN)
    assertThat(font.font.family).isEqualTo(familyFont.family)
    assertThat(resolver.fonts(FontFamilies.of(family))).isSameAs(fonts)
    assertThat(editor.view.publishFontFamilies(listOf(FontFamilies.of(family)))).isFalse()
    assertThat(editor.view.fontResolver).isSameAs(resolver)
  }

  @Test
  fun `values with the same installed families share their fonts`(): Unit = withEditor { editor ->
    val family = installedFamilies().first()
    val installed = FontFamilies.of(family)
    val withMissingFamily = FontFamilies.of(MISSING_FAMILY, family)

    editor.view.publishFontFamilies(listOf(installed, withMissingFamily))

    val resolver = editor.view.fontResolver
    assertThat(resolver.fonts(withMissingFamily)).isSameAs(resolver.fonts(installed)).isNotSameAs(resolver.editorFonts())
  }

  @Test
  fun `a value without installed families gets the editor fonts and is published`(): Unit = withEditor { editor ->
    val value = FontFamilies.of(MISSING_FAMILY)

    assertThat(editor.view.publishFontFamilies(listOf(value))).isTrue()

    val resolver = editor.view.fontResolver
    assertThat(resolver.isPublished(value)).isTrue()
    assertThat(resolver.fonts(value)).isSameAs(resolver.editorFonts())
    assertThat(editor.view.publishFontFamilies(listOf(value))).isFalse()
  }

  @Test
  fun `a settings change keeps the published values`(): Unit = withEditor { editor ->
    val value = FontFamilies.of(installedFamilies().first())
    editor.view.publishFontFamilies(listOf(value))
    val resolver = editor.view.fontResolver

    editor.reinitSettings()

    val newResolver = editor.view.fontResolver
    assertThat(newResolver).isNotSameAs(resolver)
    assertThat(newResolver.publishedFontFamilies()).containsExactly(value)
    assertThat(newResolver.fonts(value)).isNotSameAs(newResolver.editorFonts()).isNotSameAs(resolver.fonts(value))
  }

  @Test
  fun `a render context change keeps the published values`(): Unit = withEditor { editor ->
    val value = FontFamilies.of(installedFamilies().first())
    editor.view.publishFontFamilies(listOf(value))
    val fontsBefore = editor.view.fontResolver.fonts(value)

    paintScaled(editor)

    val resolver = editor.view.fontResolver
    assertThat(resolver.fontRenderContext().transform.scaleX).isEqualTo(2.0)
    assertThat(resolver.publishedFontFamilies()).containsExactly(value)
    assertThat(resolver.fonts(value)).isNotSameAs(fontsBefore).isNotSameAs(resolver.editorFonts())
  }

  @Test
  @RegistryKey(key = "editor.font.families", value = "false")
  fun `with the font families off, each value gets the editor fonts`(): Unit = withEditor { editor ->
    val value = FontFamilies.of(installedFamilies().first())

    editor.view.publishFontFamilies(listOf(value))

    val resolver = editor.view.fontResolver
    assertThat(resolver.isPublished(value)).isTrue()
    assertThat(resolver.fonts(value)).isSameAs(resolver.editorFonts())
    assertThat(resolver.fontFamiliesAscent()).isZero()
    assertThat(resolver.fontFamiliesMaxCharWidth()).isZero()
  }

  @Test
  fun `a change of the font families key rebuilds the resolver`(): Unit = withEditor { editor ->
    val value = FontFamilies.of(installedFamilies().first())
    editor.view.publishFontFamilies(listOf(value))
    assertThat(editor.view.fontResolver.fonts(value)).isNotSameAs(editor.view.fontResolver.editorFonts())
    val disposable = Disposer.newDisposable()
    try {
      Registry.get("editor.font.families").setValue(false, disposable)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

      assertThat(editor.view.fontResolver.fonts(value)).isSameAs(editor.view.fontResolver.editorFonts())
    }
    finally {
      Disposer.dispose(disposable)
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertThat(editor.view.fontResolver.fonts(value)).isNotSameAs(editor.view.fontResolver.editorFonts())
  }

  @Test
  fun `the bounds cover the published font families`(): Unit = withEditor { editor ->
    val resolver = editor.view.fontResolver
    assertThat(resolver.fontFamiliesAscent()).isZero()
    assertThat(resolver.fontFamiliesDescent()).isZero()
    assertThat(resolver.fontFamiliesMaxCharWidth()).isZero()
    assertThat(resolver.maxPrintableAsciiWidth()).isEqualTo(resolver.editorFonts().maxPrintableAsciiWidth())
    val values = installedFamilies().take(2).map { FontFamilies.of(it) }

    editor.view.publishFontFamilies(values)

    val newResolver = editor.view.fontResolver
    val fonts = values.map { newResolver.fonts(it) }
    assertThat(newResolver.fontFamiliesAscent()).isEqualTo(fonts.maxOf { it.ascent() }).isPositive()
    assertThat(newResolver.fontFamiliesDescent()).isEqualTo(fonts.maxOf { it.descent() })
    assertThat(newResolver.fontFamiliesMaxCharWidth()).isEqualTo(fonts.maxOf { it.maxCharWidth() })
    val editorWidth = newResolver.editorFonts().maxPrintableAsciiWidth()
    assertThat(newResolver.maxPrintableAsciiWidth()).isEqualTo(maxOf(editorWidth, fonts.maxOf { it.maxPrintableAsciiWidth() }))
  }

  @Test
  fun `parallel publications lose no value`(): Unit = withEditor { editor ->
    val family = installedFamilies().first()
    val published = ArrayList<FontFamilies>()
    val executor = Executors.newFixedThreadPool(PUBLISHING_THREADS)
    try {
      repeat(PUBLISHING_ROUNDS) { round ->
        val values = (0 until PUBLISHING_THREADS).map { FontFamilies.of("$MISSING_FAMILY $round $it", family) }
        val barrier = CyclicBarrier(PUBLISHING_THREADS)
        val results = values.map { value ->
          executor.submit<Boolean> {
            barrier.await(10, TimeUnit.SECONDS)
            editor.view.publishFontFamilies(listOf(value))
          }
        }
        for (result in results) {
          assertThat(result.get(30, TimeUnit.SECONDS)).isTrue()
        }
        published.addAll(values)
      }
    }
    finally {
      executor.shutdownNow()
    }

    assertThat(editor.view.fontResolver.publishedFontFamilies()).containsExactlyInAnyOrderElementsOf(published)
  }

  private fun installedFamilies(): List<String> {
    val families = FontFamilyService.getAvailableFamilies().filter { !it.startsWith(".") }
    assertThat(families).hasSizeGreaterThanOrEqualTo(2)
    return families
  }

  private fun paintScaled(editor: EditorImpl) {
    val graphics = BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB).createGraphics()
    try {
      graphics.scale(2.0, 2.0)
      graphics.clipRect(0, 0, 50, 25)
      editor.view.paint(graphics)
    }
    finally {
      graphics.dispose()
    }
  }

  private fun withEditor(text: String = "ab cd\nef", test: (EditorImpl) -> Unit) {
    runInEdtAndWait {
      val factory = EditorFactory.getInstance()
      val editor = factory.createEditor(factory.createDocument(text)) as EditorImpl
      try {
        test(editor)
      }
      finally {
        factory.releaseEditor(editor)
      }
    }
  }
}
