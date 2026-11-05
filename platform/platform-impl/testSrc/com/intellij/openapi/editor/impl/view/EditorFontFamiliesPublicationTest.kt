// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Font
import java.util.concurrent.Callable

private const val FAMILY_WIDTH = 20
private const val OTHER_WIDTH = 10
private const val VISIBLE_WIDTH = 400

/**
 * 29 characters: the line fits into [VISIBLE_WIDTH] with the editor fonts and does not fit with the font family.
 */
private val LINE = "word ".repeat(6).trim()

/**
 * Checks that the editor publishes the font families of its range highlighters, so the text gets the fonts of the families.
 * [FamilyWidthFontLayoutService] gives the characters of one font family their own width.
 */
@TestApplication
@Suppress("SuspiciousPackagePrivateAccess")
internal class EditorFontFamiliesPublicationTest {
  private val family = FontFamilyService.getAvailableFamilies().first { !it.startsWith(".") }
  private val value = FontFamilies.of(family)

  @BeforeEach
  fun setUp() {
    FontLayoutService.setInstance(FamilyWidthFontLayoutService(mapOf(family to FAMILY_WIDTH), OTHER_WIDTH))
  }

  @AfterEach
  fun tearDown() {
    FontLayoutService.setInstance(null)
  }

  @Test
  fun `a highlighter with font families gets the widths of the families`(): Unit = withEditor("aaaa bbbb") { editor ->
    addHighlighter(editor.markupModel, 5, 9, attributesWithFontFamilies())

    assertThat(width(editor, 5, 9)).isEqualTo(4 * FAMILY_WIDTH)
    assertThat(width(editor, 0, 4)).isEqualTo(4 * OTHER_WIDTH)
  }

  @Test
  fun `a highlighter added on a background thread gets the widths of the families`(): Unit = withEditor("aaaa") { editor ->
    // As the daemon does, the background thread adds the highlighter to the document markup model in a read action.
    val markupModel = DocumentMarkupModel.forDocument(editor.document, editor.project, true)
    onBackgroundThread {
      addHighlighter(markupModel, 0, 4, attributesWithFontFamilies())
    }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(width(editor, 0, 4)).isEqualTo(4 * FAMILY_WIDTH)
  }

  @Test
  fun `an attributes change to font families gets the widths of the families`(): Unit = withEditor("aaaa") { editor ->
    val highlighter = addHighlighter(editor.markupModel, 0, 4, TextAttributes())
    assertThat(width(editor, 0, 4)).isEqualTo(4 * OTHER_WIDTH)

    (highlighter as RangeHighlighterEx).setTextAttributes(attributesWithFontFamilies())

    assertThat(width(editor, 0, 4)).isEqualTo(4 * FAMILY_WIDTH)
  }

  @Test
  fun `a highlighter with font families gives the soft wraps the widths of the families`(): Unit = withEditor(LINE) { editor ->
    editor.settings.isUseSoftWraps = true
    EditorTestUtil.setEditorVisibleSizeInPixels(editor, VISIBLE_WIDTH, 1000)
    assertThat(softWrapCount(editor)).isZero()

    addHighlighter(editor.markupModel, 0, LINE.length, attributesWithFontFamilies())
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(softWrapCount(editor)).isPositive()
  }

  @Test
  fun `a value stays published after its highlighter goes away`(): Unit = withEditor("aaaa") { editor ->
    val highlighter = addHighlighter(editor.markupModel, 0, 4, attributesWithFontFamilies())

    highlighter.dispose()

    assertThat(editor.view.fontResolver.isPublished(value)).isTrue()
    assertThat(width(editor, 0, 4)).isEqualTo(4 * OTHER_WIDTH)
  }

  @Test
  fun `a highlighter without font families publishes nothing`(): Unit = withEditor("aaaa") { editor ->
    val attributes = TextAttributes()
    attributes.fontType = Font.BOLD

    addHighlighter(editor.markupModel, 0, 4, attributes)

    assertThat(editor.view.fontResolver.publishedFontFamilies()).isEmpty()
  }

  private fun attributesWithFontFamilies(): TextAttributes {
    val attributes = TextAttributes()
    attributes.fontFamilies = value
    return attributes
  }

  private fun addHighlighter(markupModel: MarkupModel, start: Int, end: Int, attributes: TextAttributes): RangeHighlighter {
    return markupModel.addRangeHighlighter(start, end, HighlighterLayer.ADDITIONAL_SYNTAX, attributes, HighlighterTargetArea.EXACT_RANGE)
  }

  private fun width(editor: EditorImpl, start: Int, end: Int): Int {
    return editor.offsetToXY(end).x - editor.offsetToXY(start).x
  }

  private fun softWrapCount(editor: EditorImpl): Int {
    editor.softWrapModel.prepareToMapping()
    return editor.softWrapModel.registeredSoftWraps.size
  }

  /**
   * Runs [action] in a read action on a background thread and waits for it.
   */
  private fun onBackgroundThread(action: () -> Unit) {
    ApplicationManager.getApplication().executeOnPooledThread(Callable { runReadActionBlocking(action) }).get()
  }

  private fun withEditor(text: String, test: (EditorImpl) -> Unit) {
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
