// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val FIRST_SIZE = 17f
private const val SECOND_SIZE = 23f

/**
 * Checks the colors scheme of an editor, which keeps the state of the editor in one immutable snapshot.
 */
@TestApplication
class EditorColorSchemeDelegateSnapshotTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  @Test
  fun testAttributesSetToNullHideTheAttributesOfTheGlobalScheme(): Unit = fixture.runTest {
    initText("text")
    val key = CodeInsightColors.ERRORS_ATTRIBUTES
    assertNotNull(EditorColorsManager.getInstance().globalScheme.getAttributes(key), "The global scheme must have the attributes")
    val scheme = editor.colorsScheme

    scheme.setAttributes(key, null)

    assertNull(scheme.getAttributes(key), "The editor must keep its own null attributes")
  }

  @Test
  fun testInheritedColorMarkerShowsTheColorOfTheGlobalScheme(): Unit = fixture.runTest {
    initText("text")
    val key = EditorColors.CARET_COLOR
    val scheme = editor.colorsScheme
    val globalColor = EditorColorsManager.getInstance().globalScheme.getColor(key)

    scheme.setColor(key, Color.MAGENTA)
    assertEquals(Color.MAGENTA, scheme.getColor(key))

    scheme.setColor(key, AbstractColorsScheme.INHERITED_COLOR_MARKER)
    assertEquals(globalColor, scheme.getColor(key))
  }

  @Test
  fun testFontSizeChangeGivesFontsAndFontPreferencesOfTheNewSize(): Unit = fixture.runTest {
    initText("text")
    val scheme = editor.colorsScheme

    scheme.setEditorFontSize(FIRST_SIZE)

    val fontPreferences = scheme.fontPreferences
    assertEquals(FIRST_SIZE, scheme.editorFontSize2D)
    assertEquals(FIRST_SIZE, fontPreferences.getSize2D(fontPreferences.fontFamily))
    assertEquals(FIRST_SIZE, scheme.getFont(EditorFontType.PLAIN).size2D)
    assertEquals(FIRST_SIZE, scheme.getFont(EditorFontType.BOLD_ITALIC).size2D)
  }

  @Test
  fun testFontPreferencesKeepTheirIdentityWhenNothingChanges(): Unit = fixture.runTest {
    initText("text")
    val scheme = editor.colorsScheme
    scheme.setEditorFontSize(FIRST_SIZE)
    val fontPreferences = scheme.fontPreferences

    (editor as EditorEx).reinitSettings()

    // The soft wraps find a font change by the identity of the font preferences
    assertSame(fontPreferences, scheme.fontPreferences)
  }

  @Test
  fun testReinitKeepsTheFontsWhenNothingChanges(): Unit = fixture.runTest {
    initText("text")
    val scheme = editor.colorsScheme
    scheme.setEditorFontSize(FIRST_SIZE)
    val font = scheme.getFont(EditorFontType.PLAIN)

    (editor as EditorEx).reinitSettings()

    assertSame(font, scheme.getFont(EditorFontType.PLAIN))
  }

  @Test
  fun testBackgroundReadersSeeTheFontsOfOneFontSize() {
    fixture.runTest {
      initText("text")
      editor.colorsScheme.setEditorFontSize(FIRST_SIZE)
    }
    val scheme = fixture.editor.colorsScheme
    val stop = AtomicBoolean()
    val failure = AtomicReference<String>()
    val reader = ApplicationManager.getApplication().executeOnPooledThread {
      while (!stop.get() && failure.get() == null) {
        val fontSize = scheme.getFont(EditorFontType.PLAIN).size2D
        if (fontSize != FIRST_SIZE && fontSize != SECOND_SIZE) {
          failure.set("The editor font has the size $fontSize")
        }
        val fontPreferences = scheme.fontPreferences
        val preferencesSize = fontPreferences.getSize2D(fontPreferences.fontFamily)
        if (preferencesSize != FIRST_SIZE && preferencesSize != SECOND_SIZE) {
          failure.set("The editor font preferences have the size $preferencesSize")
        }
      }
    }
    try {
      fixture.runTest {
        repeat(200) { i ->
          scheme.setEditorFontSize(if (i % 2 == 0) SECOND_SIZE else FIRST_SIZE)
        }
      }
    }
    finally {
      stop.set(true)
      reader.get(30, TimeUnit.SECONDS)
    }
    assertTrue(failure.get() == null) { failure.get() }
  }
}
