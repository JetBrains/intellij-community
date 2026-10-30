// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

private const val TEXT = "aWb"
private const val WIDE_CHAR = 'W'
private const val WIDE_CHAR_WIDTH = 3 * EditorModelFixture.TEST_CHAR_WIDTH

/**
 * Checks that the accessible character bounds of the editor follow its text layout.
 * The layout gives [WIDE_CHAR] the width [WIDE_CHAR_WIDTH], and each other character the width of [MockFontLayoutService].
 */
@TestApplication
class AccessibleEditorCharacterBoundsTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  @Test
  fun testCharacterBoundsHaveTheLayoutWidth(): Unit = fixture.runTest {
    FontLayoutService.setInstance(MockFontLayoutService(
      { codePoint -> charWidth(codePoint).toDouble() },
      EditorModelFixture.TEST_LINE_HEIGHT,
      EditorModelFixture.TEST_DESCENT,
    ))
    initText(TEXT)
    val accessibleText = editor.contentComponent.accessibleContext.accessibleText

    for (offset in TEXT.indices) {
      val bounds = accessibleText.getCharacterBounds(offset)
      assertNotNull(bounds)
      val start = editor.offsetToXY(offset).x
      val end = editor.offsetToXY(offset + 1).x
      assertEquals(start, bounds.x, "The bounds of the character at $offset must start where the layout puts it")
      assertEquals(end - start, bounds.width, "The bounds of the character at $offset must have its layout width")
    }
  }
}

private fun charWidth(codePoint: Int): Int {
  return if (codePoint == WIDE_CHAR.code) {
    WIDE_CHAR_WIDTH
  } else {
    EditorModelFixture.TEST_CHAR_WIDTH
  }
}
