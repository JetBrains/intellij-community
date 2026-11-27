// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.BidiTextDirection
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.impl.EditorViewAccessor
import com.intellij.openapi.editor.impl.editorModelFixture
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Font
import java.awt.Point
import java.awt.event.HierarchyEvent
import java.text.Bidi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.BorderFactory
import javax.swing.JComponent

@TestApplication
class EditorViewSnapshotTest {
  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  private val editorEx: EditorEx get() = fixture.editor as EditorEx

  @Test
  fun `test concurrent reads see consistent metrics while the view reinitializes`(): Unit = fixture.runTest {
    initText("a\tb\nlonger line of text\n")
    val view = EditorViewAccessor.getView(editor)
    val stop = AtomicBoolean()
    val started = CountDownLatch(1)
    val failure = AtomicReference<Throwable>()
    val reader = ApplicationManager.getApplication().executeOnPooledThread {
      try {
        while (!stop.get()) {
          val lineHeight = view.lineHeight
          val descent = view.descent
          assertEquals(
            lineHeight - descent, view.ascent,
            "The ascent must match the line height and the descent of one snapshot",
          )
          assertTrue(
            view.caretHeight > 0,
            "The caret height must be positive",
          )
          started.countDown()
        }
      }
      catch (e: Throwable) {
        failure.set(e)
      } finally {
        started.countDown()
      }
    }
    try {
      assertTrue(
        started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
        "The reader made no read",
      )
      repeat(REINIT_COUNT) {
        editorEx.reinitSettings()
      }
      UIUtil.dispatchAllInvocationEvents()
    } finally {
      stop.set(true)
      reader.get()
    }
    failure.get()?.let {
      throw AssertionError("A concurrent reader saw an inconsistent snapshot", it)
    }
  }

  @Test
  fun `test caret height follows the full line height cursor setting`(): Unit = fixture.runTest {
    initText("text")
    val view = EditorViewAccessor.getView(editor)
    editor.settings.isFullLineHeightCursor = true
    assertEquals(
      view.lineHeight,
      view.caretHeight,
      "A full line height caret is exactly the line height",
    )
    editor.settings.isFullLineHeightCursor = false
    assertEquals(
      view.lineHeight + view.topOverhang + view.bottomOverhang,
      view.caretHeight,
      "A default caret adds both overhangs",
    )
  }

  @Test
  fun `test a global bidi direction change reaches the view without a refresh of all editors`(): Unit = fixture.runTest {
    initText("text")
    val view = EditorViewAccessor.getView(editor)
    val global = EditorSettingsExternalizable.getInstance()
    val original = global.bidiTextDirection
    try {
      global.bidiTextDirection = BidiTextDirection.RTL
      assertEquals(
        Bidi.DIRECTION_RIGHT_TO_LEFT,
        view.bidiFlags,
        "The view takes the bidi direction from the editor settings",
      )
    } finally {
      global.bidiTextDirection = original
    }
  }

  @Test
  fun `test the RTL setting of one editor reaches its view`(): Unit = fixture.runTest {
    initText("text")
    val view = EditorViewAccessor.getView(editor)
    val rtlEnabled = editor.settings.isRtlEnabled
    editor.settings.isRtlEnabled = !rtlEnabled
    assertEquals(
      !rtlEnabled,
      view.isRtlEnabled,
      "The view takes the RTL setting from the editor settings",
    )
  }

  @Test
  fun `test the caret repaint metrics drop the top overhang for a full line height caret`(): Unit = fixture.runTest {
    initText("text")
    val view = EditorViewAccessor.getView(editor)
    editor.settings.isFullLineHeightCursor = true
    assertEquals(
      0,
      EditorViewAccessor.getCaretTopOverhang(editor),
      "A full line height caret starts at the line top, so it overhangs nothing",
    )
    editor.settings.isFullLineHeightCursor = false
    assertEquals(
      view.topOverhang,
      EditorViewAccessor.getCaretTopOverhang(editor),
      "A default caret starts at the top overhang of the font",
    )
  }

  @Test
  fun `test a prefix with a text and no attributes is rejected`(): Unit = fixture.runTest {
    initText("text")
    val failure = assertThrows<IllegalArgumentException> {
      editorEx.setPrefixTextAndAttributes("prompt> ", null)
    }
    assertTrue(
      failure.message!!.contains("prompt> "),
      "The message must name the text, but it was '${failure.message}'",
    )
  }

  @Test
  fun `test a prefix without a text needs no attributes`(): Unit = fixture.runTest {
    initText("text")
    editorEx.setPrefixTextAndAttributes(null, null)
    assertEquals(0, editorEx.prefixTextWidthInPixels)
    editorEx.setPrefixTextAndAttributes("", null)
    assertEquals(0, editorEx.prefixTextWidthInPixels)
  }

  @Test
  fun `test a rejected prefix keeps the previous one`(): Unit = fixture.runTest {
    initText("text")
    val attributes = TextAttributes(null, null, null, null, Font.PLAIN)
    editorEx.setPrefixTextAndAttributes("prompt> ", attributes)
    val width = editorEx.prefixTextWidthInPixels
    assertTrue(
      width > 0,
      "The prefix must have a width, but it was $width",
    )
    assertThrows<IllegalArgumentException> {
      editorEx.setPrefixTextAndAttributes("other> ", null)
    }
    assertEquals(
      width,
      editorEx.prefixTextWidthInPixels,
      "A rejected prefix leaves the previous one in place",
    )
  }

  @Test
  fun `test a border set after the first read moves the text`(): Unit = fixture.runTest {
    initText("text")
    assertEquals(
      Point(0, 0),
      editor.offsetToXY(0),
      "Without a border the text starts at the origin",
    )
    editor.contentComponent.border = BorderFactory.createEmptyBorder(3, 7, 0, 0)
    assertEquals(
      Point(7, 3),
      editor.offsetToXY(0),
      "The text starts at the insets of the new border",
    )
  }

  @Test
  fun `test a UI update takes the insets of a border at the new scale`(): Unit = fixture.runTest {
    initText("text")
    val originalScale = JBUIScale.scale(1f)
    try {
      JBUIScale.setUserScaleFactorForTest(1f)
      editor.contentComponent.border = JBUI.Borders.empty(3, 7, 0, 0)
      assertEquals(
        Point(7, 3),
        editor.offsetToXY(0),
        "The text starts at the insets of the border at the scale 1",
      )
      JBUIScale.setUserScaleFactorForTest(2f)
      editor.contentComponent.updateUI()
      assertEquals(
        Point(14, 6),
        editor.offsetToXY(0),
        "After the UI update the text starts at the insets of the border at the scale 2",
      )
    } finally {
      JBUIScale.setUserScaleFactorForTest(originalScale)
    }
  }

  @Test
  fun `test an editor that becomes showing takes the insets of a border at the new scale`(): Unit = fixture.runTest {
    initText("text")
    val view = EditorViewAccessor.getView(editor)
    val originalScale = JBUIScale.scale(1f)
    try {
      JBUIScale.setUserScaleFactorForTest(1f)
      editor.contentComponent.border = JBUI.Borders.empty(3, 7, 0, 0)
      JBUIScale.setUserScaleFactorForTest(2f)
      view.hierarchyChanged(showingEvent())
      assertEquals(
        Point(14, 6),
        editor.offsetToXY(0),
        "An editor that becomes showing takes the insets of the border at the scale 2",
      )
    } finally {
      JBUIScale.setUserScaleFactorForTest(originalScale)
    }
  }

  @Test
  fun `test a change of the returned insets leaves the view alone`(): Unit = fixture.runTest {
    initText("text")
    val view = EditorViewAccessor.getView(editor)
    editor.contentComponent.border = BorderFactory.createEmptyBorder(3, 7, 0, 0)
    view.insets.left = 100
    assertEquals(
      Point(7, 3),
      editor.offsetToXY(0),
      "The caller owns the returned insets, so the text stays at the insets of the border",
    )
  }

  private fun showingEvent(): HierarchyEvent {
    val shownComponent = object : JComponent() {
      override fun isShowing(): Boolean {
        return true
      }
    }
    return HierarchyEvent(
      shownComponent,
      HierarchyEvent.HIERARCHY_CHANGED,
      shownComponent,
      null,
      HierarchyEvent.SHOWING_CHANGED.toLong(),
    )
  }

  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private const val REINIT_COUNT = 200
    private const val TIMEOUT_SECONDS = 30L
  }
}
