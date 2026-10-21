// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.accessibility.ScreenReader
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
@TestApplication
class AccessibleFoldedCaretNavigationTest {
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

  @Test
  fun `test walking right over a placeholder reads it like plain text`(): Unit = fixture.runTest {
    val overCharacters = walk(initPlainEditor(), IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT, 0)
    val overPlaceholder = walk(initFoldedEditor(), IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT, 0)
    assertWalksAgree(overCharacters, overPlaceholder)
  }

  @Test
  fun `test walking left over a placeholder reads it like plain text`(): Unit = fixture.runTest {
    val end = VISIBLE_TEXT.length
    val overCharacters = walk(initPlainEditor(), IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT, end)
    val overPlaceholder = walk(initFoldedEditor(), IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT, end)
    assertWalksAgree(overCharacters, overPlaceholder)
  }

  /**
   * The other direction: a screen reader may put the caret at every on-screen offset, also inside a placeholder, and reads the
   * same offset back. This does not expand what the user collapsed.
   */
  @Test
  fun `test putting the caret at an on-screen offset round trips`(): Unit = fixture.runTest {
    val editor = initFoldedEditor()
    val context = editor.contentComponent.accessibleContext
    val text = context.accessibleText

    for (offset in 0..VISIBLE_TEXT.length) {
      context.accessibleEditableText.selectText(offset, offset)
      assertEquals(offset, text.caretPosition)
      assertEquals(offset, text.selectionStart, "an empty selection sits where the caret is")
      assertEquals(offset, text.selectionEnd)
    }
    assertEquals(VISIBLE_TEXT, visibleText(editor), "putting the caret inside a placeholder must not expand the fold")
  }

  /**
   * Some screen readers find word starts in the visible text and move the caret to each one.
   * They read the caret position after each move. A word start inside a placeholder must move the caret forward.
   */
  @Test
  fun `test setting the caret at each word start walks through placeholders`(): Unit = fixture.runTest {
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
    assertEquals(wordStarts.drop(1), caretStops, "the caret must stop at each word start after the first one")
    assertEquals(visibleText, visibleText(editor), "setting the caret must not expand the folds")
  }

  private fun assertWalksAgree(overCharacters: List<String>, overPlaceholder: List<String>) {
    assertEquals(overCharacters.joinToString("\n"), overPlaceholder.joinToString("\n"),
                 "moving the caret over a placeholder must read it out like the characters it stands in for")
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
      fixture.executeAction(actionId)
      describe(press, text.caretPosition, movements, editableText)
    }

    context.removePropertyChangeListener(listener)
    assertEquals(VISIBLE_TEXT, visibleText(editor), "walking the caret must leave the text on screen as it was")
    return walk
  }

  private fun initPlainEditor(): Editor {
    fixture.initText(VISIBLE_TEXT)
    val editor = fixture.editor
    assertNotNull(editor.contentComponent.accessibleContext)
    return editor
  }

  private fun initFoldedEditor(): Editor {
    fixture.initText(DOCUMENT_TEXT)
    val editor = fixture.editor
    // the accessible context has to exist before the fold appears, otherwise there is nothing to notify
    assertNotNull(editor.contentComponent.accessibleContext)
    assertNotNull(EditorTestUtil.addFoldRegion(editor, FOLD_START, FOLD_END, PLACEHOLDER, true))
    assertEquals(VISIBLE_TEXT, visibleText(editor), "the folded editor must show exactly what the plain one contains")
    return editor
  }

  private companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
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
