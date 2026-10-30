// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

private const val CHAR_WIDTH = 10
private const val NON_BREAKING_SPACE = 0xA0.toChar()

/**
 * Checks when [EditorView] creates a new [EditorFontResolver], and that the layout and the soft wraps use its fonts.
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
  fun `the resolver measures with the font preferences of the editor`(): Unit = withEditor { editor ->
    val preferences = editor.view.fontResolver.editorFonts().preferences()
    assertThat(preferences).isSameAs(editor.colorsScheme.fontPreferences.snapshot)
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

    val graphics = BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB).createGraphics()
    try {
      graphics.scale(2.0, 2.0)
      graphics.clipRect(0, 0, 50, 25)
      editor.view.paint(graphics)
    }
    finally {
      graphics.dispose()
    }

    val newResolver = editor.view.fontResolver
    assertThat(newResolver).isNotSameAs(resolver)
    assertThat(newResolver.fontRenderContext().transform.scaleX).isEqualTo(2.0)
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
