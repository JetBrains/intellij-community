// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.HintHint
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.KeyStroke

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

  /**
   * Test for IJPL-192327 memory leak via EditorFragmentComponent image
   */
  fun testReleaseImagesDropsSnapshotComponent() {
    myFixture.configureByText(PlainTextFileType.INSTANCE, "line1\nline2")
    val editor = myFixture.editor

    val fragmentComponent = EditorFragmentComponent.createEditorFragmentComponent(editor, 0, 1, true, false)
    assertEquals(1, fragmentComponent.componentCount)

    val hint = EditorFragmentComponent.createEditorFragmentHintForTest(fragmentComponent)
    hint.hide(false)
    assertEquals(0, fragmentComponent.componentCount)
  }

  /**
   * Test for IJPL-192327 memory leak via EditorFragmentComponent image
   */
  fun testReleaseImagesWhenEscHidesHint() {
    myFixture.configureByText(PlainTextFileType.INSTANCE, "line1\nline2")
    val editor = myFixture.editor

    val fragmentComponent = EditorFragmentComponent.createEditorFragmentComponent(editor, 0, 1, true, false)
    val hint = EditorFragmentComponent.createEditorFragmentHintForTest(fragmentComponent)

    val rootPane = JRootPane()
    rootPane.size = Dimension(100, 100)
    rootPane.layeredPane.size = rootPane.size
    val parentComponent = object : JComponent() {
      override fun isShowing(): Boolean = true
    }
    parentComponent.size = rootPane.size
    rootPane.contentPane.add(parentComponent)

    hint.show(parentComponent, 0, 0, null, HintHint(parentComponent, Point()))
    val escapeAction = fragmentComponent.getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0))
    assertNotNull(escapeAction)
    escapeAction.actionPerformed(ActionEvent(fragmentComponent, ActionEvent.ACTION_PERFORMED, null))
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertEquals(0, fragmentComponent.componentCount)
  }
}
