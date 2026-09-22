// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.EditorTestUtil
import com.intellij.util.ui.accessibility.ScreenReader
import java.beans.PropertyChangeListener
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleEditableText

/**
 * A collapsed fold is text on screen like any other, so walking the caret over its placeholder has to read out one
 * character per keystroke.
 *
 * The two walks below cover the same visible line twice - once in an editor where `...` is a fold placeholder, once
 * in an editor that really contains those three characters - and compare what a screen reader is told along the way. What
 * it reads out is driven by the [AccessibleContext.ACCESSIBLE_CARET_PROPERTY] notifications, so the walks are
 * recorded through those.
 */
class AccessibleFoldedCaretNavigationTest : AbstractEditorTest() {
  /** [ScreenReader.setActive] is a process-wide static with no restore API, so its prior value is saved by hand. */
  private var screenReaderWasActive = false

  override fun setUp() {
    super.setUp()
    screenReaderWasActive = ScreenReader.isActive()
    ScreenReader.setActive(true)
  }

  override fun tearDown() {
    try {
      ScreenReader.setActive(screenReaderWasActive)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun `test walking right over a placeholder reads it like plain text`() {
    val overCharacters = walk(initPlainEditor(), IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT, 0)
    val overPlaceholder = walk(initFoldedEditor(), IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT, 0)
    assertWalksAgree(overCharacters, overPlaceholder)
  }

  fun `test walking left over a placeholder reads it like plain text`() {
    val end = VISIBLE_TEXT.length
    val overCharacters = walk(initPlainEditor(), IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT, end)
    val overPlaceholder = walk(initFoldedEditor(), IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT, end)
    assertWalksAgree(overCharacters, overPlaceholder)
  }

  /**
   * The other direction: a screen reader may put the caret at every on-screen offset, also inside a placeholder, and reads the
   * same offset back. This does not expand what the user collapsed.
   */
  fun `test putting the caret at an on-screen offset round trips`() {
    val editor = initFoldedEditor()
    val context = editor.contentComponent.accessibleContext
    val text = context.accessibleText

    for (offset in 0..VISIBLE_TEXT.length) {
      context.accessibleEditableText.selectText(offset, offset)
      assertEquals(offset, text.caretPosition)
      assertEquals("an empty selection sits where the caret is", offset, text.selectionStart)
      assertEquals(offset, text.selectionEnd)
    }
    assertEquals("putting the caret inside a placeholder must not expand the fold", VISIBLE_TEXT, visibleText(editor))
  }

  /**
   * Some screen readers find word starts in the visible text and move the caret to each one.
   * They read the caret position after each move. A word start inside a placeholder must move the caret forward.
   */
  fun `test setting the caret at each word start walks through placeholders`() {
    // a one-line method: the folds hide the line breaks around the statement
    val documentText = "get() {\n  return x;\n}"
    initText(documentText)
    val editor = editor
    val context = editor.contentComponent.accessibleContext
    assertNotNull(context)
    val statementStart = documentText.indexOf("return")
    val statementEnd = documentText.indexOf(';') + 1
    assertNotNull(EditorTestUtil.addFoldRegion(editor, "get()".length, statementStart, " { ", true))
    assertNotNull(EditorTestUtil.addFoldRegion(editor, statementEnd, documentText.length, " }", true))
    val visibleText = "get() { return x; }"
    assertEquals(visibleText, visibleText(editor))

    val text = context.accessibleText
    val wordStarts = findWordStarts(visibleText)
    val caretStops = mutableListOf<Int>()
    repeat(wordStarts.size) {
      val caret = text.caretPosition
      val next = wordStarts.firstOrNull { it > caret } ?: return@repeat
      context.accessibleEditableText.selectText(next, next)
      caretStops.add(text.caretPosition)
    }
    assertEquals("the caret must stop at each word start after the first one", wordStarts.drop(1), caretStops)
    assertEquals("setting the caret must not expand the folds", visibleText, visibleText(editor))
  }

  private fun assertWalksAgree(overCharacters: List<String>, overPlaceholder: List<String>) {
    assertEquals("moving the caret over a placeholder must read it out like the characters it stands in for",
                 overCharacters.joinToString("\n"), overPlaceholder.joinToString("\n"))
  }

  /**
   * Presses the same key as many times as the visible line has characters, recording after every press where the
   * accessibility layer puts the caret and what it tells a screen reader to read out.
   *
   * @param visibleStart the offset in the on-screen text the walk starts from
   */
  private fun walk(editor: Editor, actionId: String, visibleStart: Int): List<String> {
    val context = editor.contentComponent.accessibleContext
    val text = context.accessibleText
    val editableText = context.accessibleEditableText

    val movements = mutableListOf<Pair<Int, Int>>()
    val listener = PropertyChangeListener { event ->
      if (event.propertyName == AccessibleContext.ACCESSIBLE_CARET_PROPERTY) {
        movements.add(event.oldValue as Int to event.newValue as Int)
      }
    }
    // the accessible text works in on-screen offsets, so both editors start the walk at the same place on screen
    editableText.selectText(visibleStart, visibleStart)
    context.addPropertyChangeListener(listener)

    val walk = (1..VISIBLE_TEXT.length).map { press ->
      movements.clear()
      executeAction(actionId, editor)
      describe(press, text.caretPosition, movements, editableText)
    }

    context.removePropertyChangeListener(listener)
    assertEquals("walking the caret must leave the text on screen as it was", VISIBLE_TEXT, visibleText(editor))
    return walk
  }

  private fun initPlainEditor(): Editor {
    initText(VISIBLE_TEXT)
    val editor = editor
    assertNotNull(editor.contentComponent.accessibleContext)
    return editor
  }

  private fun initFoldedEditor(): Editor {
    initText(DOCUMENT_TEXT)
    val editor = editor
    // the accessible context has to exist before the fold appears, otherwise there is nothing to notify
    assertNotNull(editor.contentComponent.accessibleContext)
    assertNotNull(EditorTestUtil.addFoldRegion(editor, FOLD_START, FOLD_END, PLACEHOLDER, true))
    assertEquals("the folded editor must show exactly what the plain one contains", VISIBLE_TEXT, visibleText(editor))
    return editor
  }

  private companion object {
    const val DOCUMENT_TEXT = "import java.util.List;"
    const val FOLD_START = "import ".length
    /** the fold hides everything up to the semicolon, which stays on screen behind the placeholder */
    const val FOLD_END = DOCUMENT_TEXT.length - 1
    const val PLACEHOLDER = "..."
    /** what the folded editor shows, and what the editor it is compared against actually contains */
    const val VISIBLE_TEXT = "import ...;"
    val WORD_START_REGEX = Regex("""\b\w|(?<=[\w\s])[^\w\s]|^[^\w\s]""")

    /** Finds the word starts that this test uses to model screen reader navigation. */
    fun findWordStarts(line: String): List<Int> = WORD_START_REGEX.findAll(line).map { it.range.first }.toList()

    fun describe(press: Int, caret: Int, movements: List<Pair<Int, Int>>, text: AccessibleEditableText): String = buildString {
      append("press ").append(press).append(": caret at ").append(caret)
      if (movements.isEmpty()) {
        append(", nothing is read out")
      }
      for ((oldCaret, newCaret) in movements) {
        append(", reads '").append(text.getTextRange(minOf(oldCaret, newCaret), maxOf(oldCaret, newCaret))).append("'")
      }
    }

    fun visibleText(editor: Editor): String {
      val context = editor.contentComponent.accessibleContext
      return context.accessibleEditableText.getTextRange(0, context.accessibleText.charCount)
    }
  }
}
