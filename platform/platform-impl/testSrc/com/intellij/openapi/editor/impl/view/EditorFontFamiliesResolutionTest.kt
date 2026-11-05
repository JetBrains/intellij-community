// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.highlighter.FragmentedEditorHighlighter
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
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
private const val KEY_NAME = "EDITOR_FONT_FAMILIES_RESOLUTION_TEST"
private const val MISSING_FAMILY = "No Such Font Family"

/**
 * 29 characters: the line fits into [VISIBLE_WIDTH] with the editor fonts and does not fit with the font family.
 */
private val LINE = "word ".repeat(6).trim()

/**
 * Checks that the editor resolves the font families of its range highlighters and of its scheme, so the text gets the fonts of the families.
 * [FamilyFontLayoutService] gives the characters of one font family their own width.
 */
@TestApplication
@Suppress("SuspiciousPackagePrivateAccess")
internal class EditorFontFamiliesResolutionTest {
  private val family = FontFamilyService.getAvailableFamilies().first { !it.startsWith(".") }
  private val value = FontFamilies.of(family)
  private val key = TextAttributesKey.createTextAttributesKey(KEY_NAME)

  @BeforeEach
  fun setUp() {
    FontLayoutService.setInstance(FamilyFontLayoutService(mapOf(family to FAMILY_WIDTH), OTHER_WIDTH))
  }

  @AfterEach
  fun tearDown() {
    FontLayoutService.setInstance(null)
    TextAttributesKey.removeTextAttributesKey(KEY_NAME)
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
  fun `a value stays resolved after its highlighter goes away`(): Unit = withEditor("aaaa") { editor ->
    val highlighter = addHighlighter(editor.markupModel, 0, 4, attributesWithFontFamilies())

    highlighter.dispose()

    assertThat(editor.view.fontResolver.isResolved(value)).isTrue()
    assertThat(width(editor, 0, 4)).isEqualTo(4 * OTHER_WIDTH)
  }

  @Test
  fun `a highlighter without font families resolves nothing`(): Unit = withEditor("aaaa") { editor ->
    val attributes = TextAttributes()
    attributes.fontType = Font.BOLD

    addHighlighter(editor.markupModel, 0, 4, attributes)

    assertThat(editor.view.fontResolver.resolvedFontFamilies()).isEmpty()
  }

  @Test
  fun `a new editor gets the font families of the existing highlighters`() {
    val document = createDocument("aaaa")
    addHighlighter(DocumentMarkupModel.forDocument(document, null, true), 0, 4, attributesWithFontFamilies())

    withEditor(document) { editor ->
      assertThat(editor.view.fontResolver.isResolved(value)).isTrue()
      assertThat(width(editor, 0, 4)).isEqualTo(4 * FAMILY_WIDTH)
    }
  }

  @Test
  fun `existing highlighters without font families resolve nothing`() {
    val document = createDocument("aaaa")
    val attributes = TextAttributes()
    attributes.fontType = Font.BOLD
    addHighlighter(DocumentMarkupModel.forDocument(document, null, true), 0, 4, attributes)

    withEditor(document) { editor ->
      assertThat(editor.view.fontResolver.resolvedFontFamilies()).isEmpty()
    }
  }

  @Test
  fun `an existing highlighter that lost its font families resolves nothing`() {
    val document = createDocument("aaaa")
    val highlighter = addHighlighter(DocumentMarkupModel.forDocument(document, null, true), 0, 4, attributesWithFontFamilies())
    runInEdtAndWait {
      (highlighter as RangeHighlighterEx).setTextAttributes(TextAttributes())
    }

    withEditor(document) { editor ->
      assertThat(editor.view.fontResolver.resolvedFontFamilies()).isEmpty()
    }
  }

  @Test
  fun `a key of the editor scheme with font families gets the widths of the families`(): Unit = withEditor("aaaa") { editor ->
    editor.colorsScheme = schemeWithFontFamilies()

    highlightAllText(editor, key)

    assertThat(editor.view.fontResolver.isResolved(value)).isTrue()
    assertThat(width(editor, 0, 4)).isEqualTo(4 * FAMILY_WIDTH)
  }

  @Test
  fun `a scheme change resolves the font families of the new scheme`(): Unit = withEditor("aaaa") { editor ->
    highlightAllText(editor, key)
    assertThat(width(editor, 0, 4)).isEqualTo(4 * OTHER_WIDTH)

    editor.colorsScheme = schemeWithFontFamilies()

    assertThat(width(editor, 0, 4)).isEqualTo(4 * FAMILY_WIDTH)
  }

  @Test
  fun `the attributes that the editor sets for itself resolve at the next reinit`(): Unit = withEditor("aaaa") { editor ->
    editor.colorsScheme.setAttributes(key, attributesWithFontFamilies())
    assertThat(editor.view.fontResolver.isResolved(value)).isFalse()

    editor.reinitSettings()

    assertThat(editor.view.fontResolver.isResolved(value)).isTrue()
  }

  @Test
  fun `the layout records a value that no source resolved, and the request lays out the text again`(): Unit = withEditor("aaaa") { editor ->
    highlightAllTextWithoutKey(editor, value)
    assertThat(width(editor, 0, 4)).isEqualTo(4 * OTHER_WIDTH)
    assertThat(editor.unresolvedFontFamiliesRecordCount).isEqualTo(1)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(editor.view.fontResolver.isResolved(value)).isTrue()
    assertThat(width(editor, 0, 4)).isEqualTo(4 * FAMILY_WIDTH)
  }

  @Test
  fun `the soft wraps of a value that no source resolved use the widths of the families after the request`(): Unit = withEditor(LINE) { editor ->
    highlightAllTextWithoutKey(editor, value)
    editor.settings.isUseSoftWraps = true
    EditorTestUtil.setEditorVisibleSizeInPixels(editor, VISIBLE_WIDTH, 1000)
    assertThat(softWrapCount(editor)).isZero()

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(softWrapCount(editor)).isPositive()
  }

  @Test
  fun `the soft wrap measuring records a value that no source resolved`(): Unit = withEditor("aaaa") { editor ->
    highlightAllTextWithoutKey(editor, value)
    val before = editor.unresolvedFontFamiliesRecordCount
    val view = editor.view
    val iterator = WrapElementMeasuringIterator(view, view.snapshot, editor.document.documentText, 0, 4, listOf(), listOf(), listOf())

    var x = 0f
    while (!iterator.atEnd()) {
      x = iterator.getElementEndX(x)
      iterator.advance()
    }

    assertThat(x).isEqualTo(4f * OTHER_WIDTH)
    assertThat(editor.unresolvedFontFamiliesRecordCount - before).isEqualTo(1)
  }

  @Test
  fun `a value without installed families is recorded once`(): Unit = withEditor("aaaa") { editor ->
    highlightAllTextWithoutKey(editor, FontFamilies.of(MISSING_FAMILY))
    width(editor, 0, 4)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    width(editor, 0, 4)
    editor.reinitSettings()
    width(editor, 0, 4)

    assertThat(editor.unresolvedFontFamiliesRecordCount).isEqualTo(1)
  }

  @Test
  fun `a resolved value is not recorded`(): Unit = withEditor("aaaa") { editor ->
    editor.view.resolveFontFamilies(listOf(value))
    highlightAllTextWithoutKey(editor, value)

    assertThat(width(editor, 0, 4)).isEqualTo(4 * FAMILY_WIDTH)
    assertThat(editor.unresolvedFontFamiliesRecordCount).isZero()
  }

  @Test
  fun `a value that the editor highlighter declares is resolved when the editor gets the highlighter`(): Unit = withEditor("aaaa") { editor ->
    val highlighter = DeclaringHighlighter()
    highlighter.declare(value, 4)

    editor.highlighter = highlighter

    assertThat(editor.view.fontResolver.isResolved(value)).isTrue()
    assertThat(width(editor, 0, 4)).isEqualTo(4 * FAMILY_WIDTH)
    assertThat(editor.unresolvedFontFamiliesRecordCount).isZero()
  }

  @Test
  fun `a declared value gives the soft wraps of a line that the fast path would skip without a layout`(): Unit = withEditor(LINE) { editor ->
    val highlighter = DeclaringHighlighter()
    highlighter.declare(value, LINE.length)
    editor.highlighter = highlighter

    editor.settings.isUseSoftWraps = true
    EditorTestUtil.setEditorVisibleSizeInPixels(editor, VISIBLE_WIDTH, 1000)

    assertThat(softWrapCount(editor)).isPositive()
    assertThat(editor.unresolvedFontFamiliesRecordCount).isZero()
  }

  @Test
  fun `a fragment of a highlighter with font families is resolved when the editor gets it`(): Unit = withEditor("aaaa") { source ->
    // The scheme of the editor that gets the fragment gives the key no font families, so only the fragment declares them.
    source.colorsScheme = schemeWithFontFamilies()
    highlightAllText(source, key)
    val fragment = FragmentedEditorHighlighter(source.highlighter, TextRange(0, 4))
    assertThat(fragment.textAttributesFontFamilies).containsExactly(value)

    withEditor("aaaa") { editor ->
      editor.highlighter = fragment

      assertThat(editor.view.fontResolver.isResolved(value)).isTrue()
      assertThat(width(editor, 0, 4)).isEqualTo(4 * FAMILY_WIDTH)
      assertThat(editor.unresolvedFontFamiliesRecordCount).isZero()
    }
  }

  @Test
  fun `a fragment of a highlighter without font families declares nothing`(): Unit = withEditor("aaaa") { source ->
    highlightAllText(source, key)
    val fragment = FragmentedEditorHighlighter(source.highlighter, TextRange(0, 2))

    assertThat(fragment.textAttributesFontFamilies).isEmpty()
  }

  @Test
  fun `a value that the editor highlighter declares later is resolved at its repaint`(): Unit = withEditor("aaaa") { editor ->
    val highlighter = DeclaringHighlighter()
    editor.highlighter = highlighter
    assertThat(editor.view.fontResolver.isResolved(value)).isFalse()
    assertThat(width(editor, 0, 4)).isEqualTo(4 * OTHER_WIDTH)

    highlighter.declare(value, 4)

    assertThat(editor.view.fontResolver.isResolved(value)).isTrue()
    assertThat(width(editor, 0, 4)).isEqualTo(4 * FAMILY_WIDTH)
  }

  @Test
  fun `a value that the editor highlighter declares later recalculates the soft wraps after the request`(): Unit = withEditor(LINE) { editor ->
    val highlighter = DeclaringHighlighter()
    editor.highlighter = highlighter
    editor.settings.isUseSoftWraps = true
    EditorTestUtil.setEditorVisibleSizeInPixels(editor, VISIBLE_WIDTH, 1000)
    assertThat(softWrapCount(editor)).isZero()

    highlighter.declare(value, LINE.length)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertThat(softWrapCount(editor)).isPositive()
    assertThat(editor.unresolvedFontFamiliesRecordCount).isZero()
  }

  /**
   * Returns a scheme that gives [key] the attributes with [value]. Its parent is the global scheme.
   */
  private fun schemeWithFontFamilies(): EditorColorsSchemeImpl {
    val scheme = EditorColorsSchemeImpl(EditorColorsManager.getInstance().globalScheme)
    scheme.setAttributes(key, attributesWithFontFamilies())
    return scheme
  }

  private fun createDocument(text: String): Document {
    return EditorFactory.getInstance().createDocument(text)
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
    withEditor(createDocument(text), test)
  }

  private fun withEditor(document: Document, test: (EditorImpl) -> Unit) {
    runInEdtAndWait {
      val factory = EditorFactory.getInstance()
      val editor = factory.createEditor(document) as EditorImpl
      try {
        test(editor)
      }
      finally {
        factory.releaseEditor(editor)
      }
    }
  }
}
