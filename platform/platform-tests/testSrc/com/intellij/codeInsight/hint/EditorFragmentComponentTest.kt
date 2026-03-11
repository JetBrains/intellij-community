// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Color


class EditorFragmentComponentTest : BasePlatformTestCase() {

  /**
   * Test for IJPL-189674 Extract method breaks editor background
   */
  fun testBackgroundColorIsRestoredWhenFragmentCreationFailsForInvalidLineRange() {
    val originalBackground = Color(0x12, 0x34, 0x56)
    val previewBackground = Color(0x65, 0x43, 0x21)
    myFixture.configureByText(PlainTextFileType.INSTANCE, "line1\nline2")
    val editor = myFixture.editor as EditorEx
    val invalidStartLine = editor.document.lineCount
    val invalidEndLine = invalidStartLine + 1
    val scheme = object : EditorColorsSchemeImpl(DefaultColorsScheme()) {
      init {
        initFonts()
      }
    }
    scheme.setColor(EditorColors.CARET_ROW_COLOR, previewBackground)
    editor.colorsScheme = scheme
    editor.setBackgroundColor(originalBackground)
    assertThrows(IndexOutOfBoundsException::class.java) {
      EditorFragmentComponent.createEditorFragmentComponent(
        editor,
        invalidStartLine,
        invalidEndLine,
        true,
        true,
      )
    }
    assertEquals(originalBackground, editor.backgroundColor)
  }
}
