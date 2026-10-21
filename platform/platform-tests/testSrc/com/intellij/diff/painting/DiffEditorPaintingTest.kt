// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.painting

import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.TextDiffType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorPaintingTestCase
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.SeparatorPlacement
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle

@TestDataPath($$"$PROJECT_ROOT/community/platform/diff-impl/tests/testData/diff/painting")
@TestApplication
class DiffEditorPaintingTest : EditorPaintingTestCase() {
  override fun getTestDataPath(): String {
    if (ExperimentalUI.isNewUI()) {
      return "platform/diff-impl/tests/testData/diff/painting/new.ui"
    }
    return "platform/diff-impl/tests/testData/diff/painting"
  }

  @Test
  fun testWholeLineChanged(): Unit = myFixture.runTest {
    initText("foo")
    DiffDrawUtil.createHighlighter(editor, 0, 1, MyDiffType, false)
    checkResultWithGutter()
  }

  @Test
  fun testOneLetterChanged(): Unit = myFixture.runTest {
    initText("foo")
    DiffDrawUtil.createHighlighter(editor, 0, 1, MyDiffType, true)
    DiffDrawUtil.createInlineHighlighter(editor, 1, 2, MyDiffType)
    checkResultWithGutter()
  }

  @Test
  fun testNewlineChanged(): Unit = myFixture.runTest {
    initText("fo\nbar")
    DiffDrawUtil.createHighlighter(editor, 0, 2, MyDiffType, true)
    DiffDrawUtil.createInlineHighlighter(editor, 2, 3, MyDiffType)
    checkResultWithGutter()
  }

  @Test
  fun testLastLineChanged(): Unit = myFixture.runTest {
    initText("foo\nx")
    DiffDrawUtil.createHighlighter(editor, 1, 2, MyDiffType, false)
    checkResultWithGutter()
  }

  @Test
  fun testLastEmptyLineChanged(): Unit = myFixture.runTest {
    initText("foo\n")
    DiffDrawUtil.createHighlighter(editor, 1, 2, MyDiffType, false)
    checkResultWithGutter()
  }

  @Test
  fun testLastLineSoftWraps(): Unit = myFixture.runTest {
    initText("foo ba biz")
    this@DiffEditorPaintingTest.configureSoftWraps(4)
    DiffDrawUtil.createHighlighter(editor, 0, 1, MyDiffType, false)
    checkResultWithGutter()
  }

  @Test
  fun testEmptyRange(): Unit = myFixture.runTest {
    initText("foo\nbar")
    DiffDrawUtil.createHighlighter(editor, 1, 1, MyDiffType, false)
    checkResultWithGutter()
  }

  @Test
  fun testEmptyRangeFirstLine(): Unit = myFixture.runTest {
    initText("foo\nbar")
    DiffDrawUtil.createHighlighter(editor, 0, 0, MyDiffType, false)
    checkResultWithGutter()
  }

  @Test
  fun testEmptyRangeLastEmptyLine(): Unit = myFixture.runTest {
    initText("foo")
    DiffDrawUtil.createHighlighter(editor, 1, 1, MyDiffType, false)
    checkResultWithGutter()
  }

  @Test
  fun testEmptyRangeLastLineSoftWraps(): Unit = myFixture.runTest {
    initText("foo ba biz")
    this@DiffEditorPaintingTest.configureSoftWraps(4)
    DiffDrawUtil.createHighlighter(editor, 1, 1, MyDiffType, false)
    checkResultWithGutter()
  }

  @Test
  fun testLineMarker(): Unit = myFixture.runTest {
    initText("fo o\nba r")
    this@DiffEditorPaintingTest.configureSoftWraps(2)

    DiffDrawUtil.createLineMarker(editor, 1, MyDiffType)
    checkResultWithGutter()
  }

  @Test
  fun testBorderLineMarker(): Unit = myFixture.runTest {
    initText("fo o\nba r")
    this@DiffEditorPaintingTest.configureSoftWraps(2)

    DiffDrawUtil.createBorderLineMarker(editor, 1, SeparatorPlacement.BOTTOM)
    DiffDrawUtil.createBorderLineMarker(editor, 1, SeparatorPlacement.TOP)
    checkResultWithGutter()
  }

  @Test
  fun testEmptyRangeWithInlayBelow1(): Unit = myFixture.runTest {
    initText("foo\nbar")
    editor.inlayModel.addBlockElement(5, true, true, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 1, MyDiffType, false)
    checkResultWithGutter()
  }

  @Test
  fun testEmptyRangeWithInlayBelow2(): Unit = myFixture.runTest {
    initText("foo\nbar")
    editor.inlayModel.addBlockElement(3, true, false, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 1, MyDiffType, false)
    checkResultWithGutter()
  }

  // TODO: move inlay inside 'changed area', as it's related to the changed text?
  @Test
  fun testRangeWithInlayBelow1(): Unit = myFixture.runTest {
    initText("foo\nbar\nbiz")
    editor.inlayModel.addBlockElement(9, true, true, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 2, MyDiffType, false)
    checkResultWithGutter()
  }

  @Test
  fun testRangeWithInlayBelow2(): Unit = myFixture.runTest {
    initText("foo\nbar\nbiz")
    editor.inlayModel.addBlockElement(7, true, false, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 2, MyDiffType, false)
    checkResultWithGutter()
  }

  @Test
  fun testEmptyRangeWithInlayAbove1(): Unit = myFixture.runTest {
    initText("foo\nbar")
    editor.inlayModel.addBlockElement(5, false, true, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 1, MyDiffType, false)
    checkResultWithGutter()
  }

  @Test
  fun testEmptyRangeWithInlayAbove2(): Unit = myFixture.runTest {
    initText("foo\nbar")
    editor.inlayModel.addBlockElement(2, false, false, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 1, MyDiffType, false)
    checkResultWithGutter()
  }

  // TODO: move inlay inside 'changed area', as it's related to the changed text?
  @Test
  fun testRangeWithInlayAbove1(): Unit = myFixture.runTest {
    initText("foo\nbar\nbiz")
    editor.inlayModel.addBlockElement(5, false, true, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 2, MyDiffType, false)
    checkResultWithGutter()
  }

  @Test
  fun testRangeWithInlayAbove2(): Unit = myFixture.runTest {
    initText("foo\nbar\nbiz")
    editor.inlayModel.addBlockElement(2, false, false, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 2, MyDiffType, false)
    checkResultWithGutter()
  }

  @Test
  fun testRangeWithInlayInside(): Unit = myFixture.runTest {
    initText("foo\nbar\nbiz\nbuzz")
    editor.inlayModel.addBlockElement(7, true, false, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 3, MyDiffType, false)
    checkResultWithGutter()
  }

  @Test
  fun testRangeWithInlayBelow1_excluded(): Unit = myFixture.runTest {
    initText("foo\nbar\nbiz")
    editor.inlayModel.addBlockElement(9, true, true, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 2, MyDiffType,
                                   DiffDrawUtil.PaintMode.EXCLUDED_EDITOR, DiffDrawUtil.PaintMode.EXCLUDED_GUTTER)
    checkResultWithGutter()
  }

  // FIXME: gutter-editor ranges are inconsistent
  @Test
  fun testRangeWithInlayBelow2_excluded(): Unit = myFixture.runTest {
    initText("foo\nbar\nbiz")
    editor.inlayModel.addBlockElement(7, true, false, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 2, MyDiffType,
                                   DiffDrawUtil.PaintMode.EXCLUDED_EDITOR, DiffDrawUtil.PaintMode.EXCLUDED_GUTTER)
    checkResultWithGutter()
  }

  // FIXME: gutter-editor ranges are inconsistent
  @Test
  fun testRangeWithInlayAbove1_excluded(): Unit = myFixture.runTest {
    initText("foo\nbar\nbiz")
    editor.inlayModel.addBlockElement(5, false, true, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 2, MyDiffType,
                                   DiffDrawUtil.PaintMode.EXCLUDED_EDITOR, DiffDrawUtil.PaintMode.EXCLUDED_GUTTER)
    checkResultWithGutter()
  }

  @Test
  fun testRangeWithInlayAbove2_excluded(): Unit = myFixture.runTest {
    initText("foo\nbar\nbiz")
    editor.inlayModel.addBlockElement(2, false, false, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 2, MyDiffType,
                                   DiffDrawUtil.PaintMode.EXCLUDED_EDITOR, DiffDrawUtil.PaintMode.EXCLUDED_GUTTER)
    checkResultWithGutter()
  }

  @Test
  fun testRangeWithInlayInside_excluded(): Unit = myFixture.runTest {
    initText("foo\nbar\nbiz\nbuzz")
    editor.inlayModel.addBlockElement(7, true, false, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 3, MyDiffType,
                                   DiffDrawUtil.PaintMode.EXCLUDED_EDITOR, DiffDrawUtil.PaintMode.EXCLUDED_GUTTER)
    checkResultWithGutter()
  }

  private object MyDiffType : TextDiffType {
    override fun getName(): String = throw UnsupportedOperationException()
    override fun getColor(editor: Editor?): Color = Color.RED
    override fun getIgnoredColor(editor: Editor?): Color = Color.BLUE
    override fun getMarkerColor(editor: Editor?): Color? = Color.GREEN
  }

  private class MyInlayRenderer : EditorCustomElementRenderer {
    override fun calcWidthInPixels(inlay: Inlay<*>): Int = 5

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
      g.color = JBColor.MAGENTA
      g.fillRect(targetRegion.x, targetRegion.y, targetRegion.width, targetRegion.height)
    }
  }
}