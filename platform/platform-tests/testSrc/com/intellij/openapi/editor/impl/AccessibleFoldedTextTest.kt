// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.accessibility.ScreenReader
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Point
import java.util.concurrent.TimeUnit
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleEditableText
import javax.accessibility.AccessibleExtendedText
import javax.accessibility.AccessibleText
import javax.swing.text.JTextComponent

/**
 * With a screen reader attached, the accessible text of the editor is the text on screen, so a collapsed fold region reads as
 * its placeholder and the lines it spans read as a single line. Without one it stays the document text.
 */
@TestApplication
class AccessibleFoldedTextTest {
  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  /** [ScreenReader.setActive] is a process-wide static with no restore API, so its prior value is saved by hand. */
  private var screenReaderWasActive = false

  @BeforeEach
  fun setUpScreenReader() {
    runInEdtAndWait {
      screenReaderWasActive = ScreenReader.isActive()
      ScreenReader.setActive(true)
    }
  }

  @AfterEach
  fun tearDown() {
    runInEdtAndWait {
      ScreenReader.setActive(screenReaderWasActive)
    }
  }

  // ---- Reading ----

  @Test
  fun `test char count is visible length`(): Unit = fixture.runTest {
    val editor = initFoldedEditor()
    assertEquals(TEXT.length, editor.document.textLength)
    assertEquals(VISIBLE_TEXT.length, accessibleText(editor).charCount)
  }

  @Test
  fun `test two document lines read as one visible line`(): Unit = fixture.runTest {
    val editor = initFoldedEditor()
    val text = accessibleText(editor) as AccessibleExtendedText

    val first = text.getTextSequenceAt(AccessibleExtendedText.LINE, 0)
    assertNotNull(first)
    assertEquals("line one...", first.text)
    assertEquals(0, first.startIndex)
    assertEquals(first.text, editableText(editor).getTextRange(first.startIndex, first.endIndex), "sequence indices are indices into the accessible text")

    val second = text.getTextSequenceAfter(AccessibleExtendedText.LINE, 0)
    assertNotNull(second)
    assertEquals("line three", second.text)
  }

  @Test
  fun `test placeholder reads as its characters and as one word`(): Unit = fixture.runTest {
    val editor = initFoldedEditor()
    val text = accessibleText(editor)
    for (offset in FOLD_START until FOLD_START + PLACEHOLDER.length) {
      assertEquals(".", text.getAtIndex(AccessibleText.CHARACTER, offset))
      assertEquals(PLACEHOLDER, text.getAtIndex(AccessibleText.WORD, offset))
      val word = (text as AccessibleExtendedText).getTextSequenceAt(AccessibleText.WORD, offset)
      assertNotNull(word)
      assertEquals(FOLD_START, word.startIndex)
      assertEquals(FOLD_START + PLACEHOLDER.length, word.endIndex)
      assertEquals(PLACEHOLDER, word.text)
    }
    assertEquals("\n", text.getAtIndex(AccessibleText.CHARACTER, FOLD_START + PLACEHOLDER.length))
  }

  @Test
  fun `test adjacent folds`(): Unit = fixture.runTest {
    initText(TEXT)
    val editor = editor
    assertNotNull(editor.contentComponent.accessibleContext)
    assertNotNull(EditorTestUtil.addFoldRegion(editor, 0, 4, "A", true))
    assertNotNull(EditorTestUtil.addFoldRegion(editor, 4, FOLD_START, "B", true))

    val remainingText = TEXT.substring(FOLD_START)
    assertVisibleLines("AB$remainingText")
    // a range over the second placeholder only: the first fold stays untouched
    editableText(editor).delete(1, 2)
    assertEquals(TEXT.substring(0, 4) + remainingText, editor.document.text)
  }

  @Test
  fun `test lines with adjacent empty and nested folds`(): Unit = fixture.runTest {
    initText("aa\nbb\ncc\ndd\nee\nff")
    val editor = editor
    assertNotNull(EditorTestUtil.addFoldRegion(editor, 3, 5, "inner", true))
    assertNotNull(EditorTestUtil.addFoldRegion(editor, 1, 6, "<>", true))
    assertNotNull(EditorTestUtil.addFoldRegion(editor, 6, 9, "", true))
    assertNotNull(EditorTestUtil.addFoldRegion(editor, 9, 12, "", true))
    assertNotNull(EditorTestUtil.addFoldRegion(editor, 13, 14, "long", true))
    assertVisibleLines("a<>elong\nff")
  }

  /** A placeholder occupies one visual line, so its own line breaks must not become accessible line boundaries. */
  @Test
  fun `test lines with a flattened placeholder and trailing separator`(): Unit = fixture.runTest {
    initText("one\ntwo\nthree\n")
    assertNotNull(EditorTestUtil.addFoldRegion(editor, 1, 8, "X\nY\rZ", true))
    assertVisibleLines("oX Y Zthree\n")
  }

  /** The macOS bridge can ask with an offset from before the text got shorter; a Swing element then gives the closest line. */
  @Test
  fun `test out of range offset maps to the closest line`(): Unit = fixture.runTest {
    val editor = initFoldedEditor()
    val root = textComponent(editor).document.defaultRootElement
    assertEquals(1, root.getElementIndex(VISIBLE_TEXT.length + 1))
    assertEquals(0, root.getElementIndex(-1))

    editor.foldingModel.runBatchFoldingOperation { collapsedFold(editor).isExpanded = true }
    assertEquals(2, root.getElementIndex(TEXT.length + 1))
  }

  // ---- Caret and selection ----

  @Test
  fun `test caret is reported in visible offsets`(): Unit = fixture.runTest {
    val editor = initFoldedEditor()
    val text = accessibleText(editor)

    // "line three" starts right after the fold in both spaces
    editor.caretModel.moveToOffset(FOLD_END + 1)
    assertEquals(VISIBLE_LINE_THREE, text.caretPosition)

    editor.caretModel.moveToOffset(FOLD_START)
    assertEquals(FOLD_START, text.selectionStart)
    assertEquals(FOLD_START, text.selectionEnd, "an empty selection must not grow over the fold")
    assertNull(text.selectedText)
  }

  @Test
  fun `test selection spanning fold reads the placeholder`(): Unit = fixture.runTest {
    val editor = initFoldedEditor()
    editor.selectionModel.setSelection(5, FOLD_END + 5)
    val text = accessibleText(editor)
    assertEquals("one...\nline", text.selectedText)
    assertEquals(5, text.selectionStart)
    assertEquals(VISIBLE_LINE_THREE + 4, text.selectionEnd)
  }

  @Test
  fun `test selecting through the accessible text maps bounds to the document`(): Unit = fixture.runTest {
    val editor = initFoldedEditor()

    editableText(editor).selectText(0, FOLD_START)
    assertEquals(0, editor.selectionModel.selectionStart)
    assertEquals(FOLD_START, editor.selectionModel.selectionEnd, "a range ending where the placeholder begins stops in front of the fold")

    editableText(editor).selectText(FOLD_START, FOLD_START)
    assertFalse(editor.selectionModel.hasSelection(), "selecting an empty range must not select the fold")
    assertEquals(FOLD_START, editor.caretModel.offset)
  }

  /**
   * A selection reaching into virtual space has no folds to map, so it reads exactly as the selection model spells it out -
   * padding included, which no range of the document text could express.
   */
  @Test
  fun `test virtual space selection is reported without folds`(): Unit = fixture.runTest {
    initText("abc")
    val editor = editor as EditorEx
    editor.settings.isVirtualSpace = true
    editor.isColumnMode = true
    editor.selectionModel.setBlockSelection(LogicalPosition(0, 1), LogicalPosition(0, 6))
    assertEquals("bc   ", editor.selectionModel.selectedText)
    assertEquals("bc   ", accessibleText(editor).selectedText)
  }

  @Test
  fun `test placeholder character bounds round trip`(): Unit = fixture.runTest {
    val editor = initFoldedEditor()
    val text = accessibleText(editor)
    for (offset in FOLD_START until FOLD_START + PLACEHOLDER.length) {
      val bounds = text.getCharacterBounds(offset)
      assertNotNull(bounds)
      val point = Point(bounds.x, bounds.y + bounds.height / 2)
      assertEquals(offset, text.getIndexAtPoint(point))
      assertEquals(offset, textComponent(editor).viewToModel2D(point))
    }
  }

  // ---- Editing ----

  /** Range bounds strictly inside a placeholder snap outwards to the fold; bounds at its start and empty ranges do not. */
  @Test
  fun `test edit ranges touching the placeholder`(): Unit = fixture.runTest {
    assertEquals(TEXT.substring(0, FOLD_START) + TEXT.substring(FOLD_END), afterDeleting(FOLD_START + 1, FOLD_START + 2))
    assertEquals(TEXT.substring(FOLD_START), afterDeleting(0, FOLD_START))
    assertEquals(TEXT, afterDeleting(FOLD_START, FOLD_START))
    assertEquals(TEXT, afterDeleting(FOLD_START + 1, FOLD_START + 1))
  }

  private fun afterDeleting(start: Int, end: Int): String {
    val editor = initFoldedEditor()
    editableText(editor).delete(start, end)
    return editor.document.text
  }

  @Test
  fun `test edits around and inside the fold are reflected`(): Unit = fixture.runTest {
    val editor = initFoldedEditor()
    val document = editor.document
    writeText(editor) { document.insertString(0, "xy") }
    assertVisibleLines("xyline one...\nline three")
    writeText(editor) { document.insertString(document.textLength, "!") }
    assertVisibleLines("xyline one...\nline three!")
    // the placeholder stands for the whole, now longer, folded text
    writeText(editor) { document.insertString(FOLD_START + 5, "zzz") }
    assertVisibleLines("xyline one...\nline three!")
    // an edit ending exactly at the fold start leaves the fold intact
    writeText(editor) { document.replaceString(FOLD_START - 1, FOLD_START + 2, "-") }
    assertVisibleLines("xyline -...\nline three!")
  }

  @Test
  fun `test changing the placeholder is reflected`(): Unit = fixture.runTest {
    val editor = initFoldedEditor()
    editor.foldingModel.runBatchFoldingOperation { collapsedFold(editor).placeholderText = "longer" }
    assertVisibleLines("line onelonger\nline three")
  }

  // ---- Folding changes ----

  @Test
  fun `test collapse and expand are announced and reflected`(): Unit = fixture.runTest {
    initText(TEXT)
    val editor = editor
    val events = textEvents(editor)

    val region = EditorTestUtil.addFoldRegion(editor, FOLD_START, FOLD_END, PLACEHOLDER, true)
    assertNotNull(region)
    assertFalse(events.isEmpty(), "collapsing a fold must notify the accessible context")
    assertVisibleLines(VISIBLE_TEXT)

    events.clear()
    editor.foldingModel.runBatchFoldingOperation { region!!.isExpanded = true }
    assertFalse(events.isEmpty(), "expanding a fold must notify the accessible context")
    assertVisibleLines(TEXT)
  }

  @Test
  fun `test text queried on a background thread during a folding change is not served afterwards`(): Unit = fixture.runTest {
    initText(TEXT)
    val editor = editor
    val text = accessibleText(editor)
    assertEquals(TEXT.length, text.charCount)

    // a background caller that reads the text in the middle of the EDT's folding change, when the folding model still
    // reports the old state
    (editor as EditorEx).foldingModel.addListener(object : FoldingListener {
      override fun onFoldProcessingStart() {
        ApplicationManager.getApplication().executeOnPooledThread { text.charCount }.get(1, TimeUnit.MINUTES)
      }
    }, testRootDisposable)

    assertNotNull(EditorTestUtil.addFoldRegion(editor, FOLD_START, FOLD_END, PLACEHOLDER, true))
    assertEquals(VISIBLE_TEXT.length, text.charCount, "text read during a folding change must not be served after it")
  }

  // ---- Without a screen reader, or without folds ----

  @Test
  fun `test without a screen reader folds are neither read nor announced`(): Unit = fixture.runTest {
    ScreenReader.setActive(false)
    initText(TEXT)
    val editor = editor
    val events = textEvents(editor)

    val region = EditorTestUtil.addFoldRegion(editor, FOLD_START, FOLD_END, PLACEHOLDER, true)
    assertNotNull(region)
    assertEquals(emptyList<String>(), events, "a fold does not change the document, so nothing may be announced")
    assertVisibleLines(TEXT)

    editor.foldingModel.runBatchFoldingOperation { region!!.isExpanded = true }
    assertEquals(emptyList<String>(), events)
  }

  @Test
  fun `test turning the screen reader off and on is picked up at once`(): Unit = fixture.runTest {
    val editor = initFoldedEditor()
    val text = accessibleText(editor)
    assertEquals(VISIBLE_TEXT.length, text.charCount)

    ScreenReader.setActive(false)
    assertEquals(TEXT.length, text.charCount, "without a screen reader the accessible text is the document, with no edit or folding change in between")

    ScreenReader.setActive(true)
    assertEquals(VISIBLE_TEXT.length, text.charCount)
  }

  @Test
  fun `test without folds the accessible text is the document`(): Unit = fixture.runTest {
    initText(TEXT)
    assertVisibleLines(TEXT)
    assertEquals(TEXT, textComponent(editor).text)
  }

  /**
   * [JTextComponent.getText] is not how a screen reader reads the editor - the [AccessibleContext] is. Ordinary
   * platform code calls it on whatever [JTextComponent] it comes across, so it has to keep reporting the document.
   */
  @Test
  fun `test JTextComponent text stays in document offsets`(): Unit = fixture.runTest {
    val editor = initFoldedEditor()
    val component = textComponent(editor)
    assertEquals(VISIBLE_TEXT, editableText(editor).getTextRange(0, accessibleText(editor).charCount))
    assertEquals(TEXT, component.text)
    assertEquals(TEXT.substring(FOLD_START, FOLD_END), component.getText(FOLD_START, FOLD_END - FOLD_START))
  }

  // ---- Helpers ----

  /**
   * Checks the on-screen text and its line structure through every channel a screen reader uses: the accessible text, and
   * the [JTextComponent] element tree the macOS bridge maps offsets to lines through.
   */
  private fun assertVisibleLines(expected: String) {
    val editor = fixture.editor
    val root = textComponent(editor).document.defaultRootElement
    val text = accessibleText(editor) as AccessibleExtendedText
    val lines = expected.split("\n")
    assertEquals(expected.length, accessibleText(editor).charCount)
    assertEquals(expected, editableText(editor).getTextRange(0, expected.length))
    assertEquals(expected.length, root.endOffset)
    assertEquals(lines.size, root.elementCount)
    var start = 0
    for ((line, lineText) in lines.withIndex()) {
      val end = start + lineText.length
      assertEquals(start, root.getElement(line).startOffset)
      assertEquals(end, root.getElement(line).endOffset)
      for (offset in start..end) {
        assertEquals(line, root.getElementIndex(offset))
        if (offset < expected.length) {
          val sequence = text.getTextSequenceAt(AccessibleExtendedText.LINE, offset)
          assertNotNull(sequence)
          assertEquals(lineText, sequence.text)
          assertEquals(start, sequence.startIndex)
        }
      }
      start = end + 1
    }
  }

  private fun initFoldedEditor(): Editor {
    fixture.initText(TEXT)
    val editor = fixture.editor
    // the accessible context must exist before the fold appears, otherwise there is nothing to notify
    assertNotNull(editor.contentComponent.accessibleContext)
    assertNotNull(EditorTestUtil.addFoldRegion(editor, FOLD_START, FOLD_END, PLACEHOLDER, true))
    return editor
  }

  private companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    /** `line one\nline two\nline three`: the fold below swallows the first line separator. */
    const val TEXT = "line one\nline two\nline three"
    const val FOLD_START = 8
    const val FOLD_END = 17
    const val PLACEHOLDER = "..."
    const val VISIBLE_TEXT = "line one...\nline three"
    val VISIBLE_LINE_THREE = VISIBLE_TEXT.indexOf("line three")

    fun textEvents(editor: Editor): MutableList<String> {
      val events = mutableListOf<String>()
      editor.contentComponent.accessibleContext.addPropertyChangeListener { event ->
        if (event.propertyName == AccessibleContext.ACCESSIBLE_TEXT_PROPERTY) {
          events.add(event.propertyName)
        }
      }
      return events
    }

    fun collapsedFold(editor: Editor): FoldRegion =
      editor.foldingModel.allFoldRegions.firstOrNull { it.isValid && !it.isExpanded }
      ?: throw AssertionError("no collapsed fold region")

    fun writeText(editor: Editor, change: Runnable) {
      WriteCommandAction.runWriteCommandAction(editor.project, change)
    }

    fun textComponent(editor: Editor): JTextComponent = editor.contentComponent as JTextComponent

    fun accessibleText(editor: Editor): AccessibleText = editor.contentComponent.accessibleContext.accessibleText

    fun editableText(editor: Editor): AccessibleEditableText = editor.contentComponent.accessibleContext.accessibleEditableText
  }
}
