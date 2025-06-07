// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.execution.filters.CompositeFilter
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.ResultItem
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.process.ConsoleHighlighter
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Expirable
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.text.allOccurrencesOf
import junit.framework.TestCase.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertFalse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@RunWith(Parameterized::class)
class EditorHyperlinkSupportTest(private val trackDocumentChangesManually: Boolean) : BasePlatformTestCase() {

  private lateinit var editor: Editor

  private val hyperlinkSupport: EditorHyperlinkSupport
    get() = EditorHyperlinkSupport.get(editor, trackDocumentChangesManually)

  private val document: Document
    get() = editor.document

  @Before
  fun init() {
    editor = EditorFactory.getInstance().createEditor(DocumentImpl("", true), project)
    Disposer.register(testRootDisposable) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "trackDocumentChangesManually: {0}")
    fun trackDocumentChangesManuallyValues(): List<Boolean> = listOf(true, false)
  }

  private fun collectAllHyperlinks(): List<RangeHighlighter> {
    hyperlinkSupport.waitForPendingFilters(AWAIT_HYPERLINK_TIMEOUT.inWholeMilliseconds)
    return EditorHyperlinkSupport.getRangeHighlighters(0, document.textLength, true, false, editor)
  }

  private fun collectAllHighlightings(): List<RangeHighlighter> {
    hyperlinkSupport.waitForPendingFilters(AWAIT_HYPERLINK_TIMEOUT.inWholeMilliseconds)
    return EditorHyperlinkSupport.getRangeHighlighters(0, document.textLength, false, true, editor)
  }

  private fun collectAllInlays(): List<Inlay<*>> {
    hyperlinkSupport.waitForPendingFilters(AWAIT_HYPERLINK_TIMEOUT.inWholeMilliseconds)
    return hyperlinkSupport.getInlays(0, document.textLength)
  }

  @Test
  fun `test custom hyperlinks survive clearing`() {
    val customLinkText = "custom-link"
    val standardLinkText = "standard-link"
    document.setText("""
        $standardLinkText // line 0
        $customLinkText   // line 1
        $standardLinkText // line 2
    """.trimIndent())

    hyperlinkSupport.highlightHyperlinksLater(MyHyperlinkFilter(standardLinkText), 0, 2, eternal())
    assertHyperlinks(standardLinkText, 2)

    val customLinkStartOffset = document.getLineStartOffset(1)
    val customHyperlink = editor.getMarkupModel().addRangeHighlighter(CodeInsightColors.HYPERLINK_ATTRIBUTES,
                                                                      customLinkStartOffset,
                                                                      customLinkStartOffset + customLinkText.length,
                                                                      HighlighterLayer.HYPERLINK,
                                                                      HighlighterTargetArea.EXACT_RANGE)
    val hyperlinks = assertHyperlinks(standardLinkText, 2)

    hyperlinkSupport.clearHyperlinks(0, document.textLength)

    assertEmpty(collectAllHyperlinks())
    assertAllInvalid(hyperlinks)
    assertValid(customHyperlink)
  }

  @Test
  fun `test custom highlightings survive clearing`() {
    val customHighlightingText = "custom-highlighting"
    val standardHighlightingText = "standard-highlighting"
    document.setText("""
        $standardHighlightingText // line 0
        $customHighlightingText   // line 1
        $standardHighlightingText // line 2
    """.trimIndent())

    hyperlinkSupport.highlightHyperlinksLater(MyHighlightingFilter(standardHighlightingText), 0, 2, eternal())
    assertHighlightings(standardHighlightingText, 2)

    val customLinkStartOffset = document.getLineStartOffset(1)
    val customHighlighting = editor.getMarkupModel().addRangeHighlighter(customLinkStartOffset,
                                                                         customLinkStartOffset + customHighlightingText.length,
                                                                         HighlighterLayer.CONSOLE_FILTER,
                                                                         editor.colorsScheme.getAttributes(ConsoleHighlighter.GREEN),
                                                                         HighlighterTargetArea.EXACT_RANGE)
    val highlightings = assertHighlightings(standardHighlightingText, 2)

    hyperlinkSupport.clearHyperlinks(0, document.textLength)

    assertEmpty(collectAllHighlightings())
    assertAllInvalid(highlightings)
    assertValid(customHighlighting)
  }

  @Test
  fun `test custom inlays survive clearing`() {
    val customInlayText = "custom-inlay"
    val standardInlayText = "standard-inlay"
    document.setText("""
        $standardInlayText // line 0
        $customInlayText   // line 1
        $standardInlayText // line 2
    """.trimIndent())

    hyperlinkSupport.highlightHyperlinksLater(MyInlayFilter(standardInlayText), 0, 2, eternal())
    assertInlays(standardInlayText, 2)

    val customInlay = editor.getInlayModel().addInlineElement<EditorCustomElementRenderer>(
      document.getLineStartOffset(1) + customInlayText.length, createEmptyInlayRenderer())!!

    val inlays = assertInlays(standardInlayText, 2)

    hyperlinkSupport.clearHyperlinks(0, document.textLength)

    assertEmpty(collectAllInlays())
    assertAllInlaysInvalid(inlays)
    assertValid(customInlay)
  }

  @Test
  fun `test hyperlinks and inlays`() {
    val hyperlinkText = "my-hyperlink"
    val inlayText = "my-inlay"
    document.setText("""
        $hyperlinkText $inlayText
        $inlayText $hyperlinkText // line 1
        $hyperlinkText            // line 2
    """.trimIndent())

    val filter = CompositeFilter(project).also {
      it.addFilter(MyHyperlinkFilter(hyperlinkText))
      it.addFilter(MyInlayFilter(inlayText))
      it.setForceUseAllFilters(true)
    }
    hyperlinkSupport.highlightHyperlinksLater(filter, 0, 2, eternal())
    val hyperlinks = assertHyperlinks(hyperlinkText, 3)
    val inlays = assertInlays(inlayText, 2)

    hyperlinkSupport.clearHyperlinks(0, document.textLength)

    assertEmpty(collectAllInlays())
    assertAllInvalid(hyperlinks)
    assertAllInlaysInvalid(inlays)
  }

  @Test
  fun `test hyperlinks, inlays and highlightings`() {
    val hyperlinkText = "my-hyperlink"
    val inlayText = "my-inlay"
    val highlightingText = "my-highlighting"
    document.setText("""
        $hyperlinkText $inlayText $highlightingText
        $highlightingText $hyperlinkText
        $hyperlinkText $inlayText
        $highlightingText $inlayText
    """.trimIndent())

    val filter = CompositeFilter(project).also {
      it.addFilter(MyHyperlinkFilter(hyperlinkText))
      it.addFilter(MyInlayFilter(inlayText))
      it.addFilter(MyHighlightingFilter(highlightingText))
      it.setForceUseAllFilters(true)
    }
    hyperlinkSupport.highlightHyperlinksLater(filter, 0, 3, eternal())
    val hyperlinks = assertHyperlinks(hyperlinkText, 3)
    val inlays = assertInlays(inlayText, 3)
    val highlightings = assertHighlightings(highlightingText, 3)

    hyperlinkSupport.clearHyperlinks(0, document.textLength)

    assertEmpty(collectAllInlays())
    assertAllInvalid(hyperlinks)
    assertAllInlaysInvalid(inlays)
    assertAllInvalid(highlightings)
  }

  @Test
  fun `test clearing adjacent links separated by space`() {
    val fooLinkText = "foo"
    val barLinkText = "bar"
    document.setText("$fooLinkText $barLinkText")
    val compositeFilter: CompositeFilter = CompositeFilter(project).also {
      it.addFilter(MyHyperlinkFilter(fooLinkText))
      it.addFilter(MyHyperlinkFilter(barLinkText))
      it.setForceUseAllFilters(true)
    }
    hyperlinkSupport.highlightHyperlinksLater(compositeFilter, 0, 0, eternal())
    val expectedFooLinkRange = TextRange.from(0, fooLinkText.length)
    val expectedBarLinkRange = TextRange.from(fooLinkText.length + 1, barLinkText.length)
    assertEquals(listOf(expectedFooLinkRange, expectedBarLinkRange), collectAllHyperlinks().map { it.textRange })

    hyperlinkSupport.clearHyperlinks(expectedFooLinkRange.startOffset, expectedFooLinkRange.endOffset)
    assertEquals(listOf(expectedBarLinkRange), collectAllHyperlinks().map { it.textRange })

    hyperlinkSupport.clearHyperlinks(expectedBarLinkRange.startOffset, expectedBarLinkRange.startOffset)
    assertEmpty(collectAllHyperlinks())
  }

  @Test
  fun `test clearing adjacent links`() {
    val fooLinkText = "foo"
    val barLinkText = "bar"
    document.setText("$fooLinkText$barLinkText")
    val filter = CompositeFilter(project).also {
      it.addFilter(MyHyperlinkFilter(fooLinkText))
      it.addFilter(MyHyperlinkFilter(barLinkText))
      it.setForceUseAllFilters(true)
    }
    hyperlinkSupport.highlightHyperlinksLater(filter, 0, 0, eternal())
    val expectedFooLinkRange = TextRange.from(0, fooLinkText.length)
    val expectedBarLinkRange = TextRange.from(fooLinkText.length, barLinkText.length)
    assertEquals(listOf(expectedFooLinkRange, expectedBarLinkRange), collectAllHyperlinks().map { it.textRange })

    // TODO make endOffset exclusive for hyperlinks/highlightings to avoid removing adjacent links, like it's happening here:
    hyperlinkSupport.clearHyperlinks(expectedFooLinkRange.startOffset, expectedFooLinkRange.endOffset)
    assertEmpty(collectAllHyperlinks())
  }

  @Test
  fun `test inclusive endOffset`() {
    val fooLinkText = "foo"
    document.setText(fooLinkText)

    val expectedFooRange = TextRange.from(0, fooLinkText.length)
    hyperlinkSupport.highlightHyperlinksLater(MyHyperlinkFilter(fooLinkText), 0, 0, eternal())
    assertEquals(listOf(expectedFooRange), collectAllHyperlinks().map { it.textRange })
    hyperlinkSupport.clearHyperlinks(expectedFooRange.endOffset, expectedFooRange.endOffset)
    assertEmpty(collectAllHyperlinks())

    hyperlinkSupport.highlightHyperlinksLater(MyHighlightingFilter(fooLinkText), 0, 0, eternal())
    assertEquals(listOf(expectedFooRange), collectAllHighlightings().map { it.textRange })
    hyperlinkSupport.clearHyperlinks(expectedFooRange.endOffset, expectedFooRange.endOffset)
    assertEmpty(collectAllHighlightings())

    hyperlinkSupport.highlightHyperlinksLater(MyInlayFilter(fooLinkText), 0, 0, eternal())
    assertEquals(listOf(expectedFooRange).map { it.endOffset }, collectAllInlays().map { it.offset })
    hyperlinkSupport.clearHyperlinks(expectedFooRange.endOffset, expectedFooRange.endOffset)
    assertEmpty(collectAllInlays())
  }

  // TODO ban empty hyperlinks and update the test
  @Test
  fun `test empty link`() {
    val fooLinkText = "foo"
    document.setText(fooLinkText)

    val expectedFooRange = TextRange.from(fooLinkText.length, 0)
    hyperlinkSupport.highlightHyperlinksLater(object: MyHyperlinkFilter(fooLinkText) {
      override val emptyAtEndOffset: Boolean get() = true
    }, 0, 0, eternal())
    assertEquals(listOf(expectedFooRange), collectAllHyperlinks().map { it.textRange })
    hyperlinkSupport.clearHyperlinks(expectedFooRange.endOffset, expectedFooRange.endOffset)
    assertEmpty(collectAllHyperlinks())
  }

  // TODO ban empty highlightings and update the test
  @Test
  fun `test empty highlighting`() {
    val fooLinkText = "foo"
    document.setText(fooLinkText)

    val expectedFooRange = TextRange.from(fooLinkText.length, 0)
    hyperlinkSupport.highlightHyperlinksLater(object: MyHighlightingFilter(fooLinkText) {
      override val emptyAtEndOffset: Boolean get() = true
    }, 0, 0, eternal())
    assertEquals(listOf(expectedFooRange), collectAllHighlightings().map { it.textRange })
    hyperlinkSupport.clearHyperlinks(expectedFooRange.endOffset, expectedFooRange.endOffset)
    assertEmpty(collectAllHighlightings())
  }

  @Test
  fun `test basic hyperlink highlighting`() {
    val linkText = "foo"
    val text = (1..5).joinToString("\n") { "-".repeat(it) + linkText }
    document.setText(text)
    hyperlinkSupport.highlightHyperlinksLater(MyHyperlinkFilter(linkText), 0, document.lineCount - 1, eternal())
    assertHyperlinks(linkText, 5)
  }

  @Test
  fun `test hyperlinks with removing trailing text`() {
    Assume.assumeTrue(trackDocumentChangesManually)
    val linkText = "foo"
    val patternLines = (1..5).map { "-".repeat(it) + linkText }
    val text = (1..10).flatMap { patternLines }.joinToString("\n")
    document.setText("$text some trailing output")
    val filter = MyHyperlinkFilter(linkText, 5.milliseconds)
    hyperlinkSupport.highlightHyperlinksLater(filter, 0, document.lineCount - 1, eternal())
    document.replaceString(text.length, document.textLength, "")
    hyperlinkSupport.clearHyperlinks(document.getLineStartOffset(document.lineCount - 1), document.textLength)
    hyperlinkSupport.highlightHyperlinksLater(filter, document.lineCount - 1, document.lineCount - 1, eternal())
    assertHyperlinks(linkText, 50)
  }

  @Test
  fun `test hyperlinks with deleting from document top`() {
    Assume.assumeTrue(trackDocumentChangesManually)
    val fooLinkText = "foo"
    val barLinkText = "bar"
    val fooLines = (1..10).flatMap {
      (1..5).map { "-".repeat(it) + " " + fooLinkText }
    }

    val barLines = (1..10).flatMap {
      (1..5).map { "-".repeat(it) + " " + barLinkText }
    }

    val filter = CompositeFilter(project).also {
      it.addFilter(MyHyperlinkFilter(fooLinkText, 5.milliseconds))
      it.addFilter(MyHyperlinkFilter(barLinkText, 5.milliseconds))
      it.setForceUseAllFilters(true)
    }

    document.setText(fooLines.joinToString("\n"))

    hyperlinkSupport.highlightHyperlinksLater(filter, 0, document.lineCount - 1, eternal())

    for (line in barLines) {
      document.deleteString(0, document.getLineEndOffset(0) + 1)
      document.insertString(document.textLength, "\n$line")
      hyperlinkSupport.highlightHyperlinksLater(filter, document.lineCount - 1, document.lineCount - 1, eternal())
    }
    assertHyperlinks(barLinkText, 50)
  }

  @Test
  fun `test hyperlinks with deleting from document top and replacing bottom`() {
    Assume.assumeTrue(trackDocumentChangesManually)
    val fooLinkText = "foo"
    val barLinkText = "bar"
    val fooLines = (1..10).flatMap {
      (1..5).map { "-".repeat(it) + " " + fooLinkText }
    }

    val barLines = (1..10).flatMap {
      (1..5).map { "-".repeat(it) + " " + barLinkText }
    }

    val filter = CompositeFilter(project).also {
      it.addFilter(MyHyperlinkFilter(fooLinkText, 5.milliseconds))
      it.addFilter(MyHyperlinkFilter(barLinkText, 5.milliseconds))
      it.setForceUseAllFilters(true)
    }

    document.setText(fooLines.joinToString("\n"))

    hyperlinkSupport.highlightHyperlinksLater(filter, 0, document.lineCount - 1, eternal())

    barLines.zipWithNext { line1, line2 ->
      document.deleteString(0, document.getLineEndOffset(0) + 1)
      val lastLineStartOffset = document.getLineStartOffset(document.lineCount - 1)
      hyperlinkSupport.clearHyperlinks(document.textLength, document.textLength)
      document.replaceString(lastLineStartOffset, document.textLength, line1 + "\n" + line2)
      hyperlinkSupport.highlightHyperlinksLater(filter, document.lineCount - 2, document.lineCount - 1, eternal())
    }
    assertHyperlinks(barLinkText, 50)
  }

  private fun assertHighlightings(textToHighlight: String, expectedCount: Int): List<RangeHighlighter> {
    val text = document.text
    val expectedRanges = text.allOccurrencesOf(textToHighlight).map {
      TextRange(it, it + textToHighlight.length)
    }.toList()
    val rangeHighlighters = collectAllHighlightings()
    for (range in rangeHighlighters) {
      assertEquals(textToHighlight, range.textRange.substring(text))
    }
    assertEquals(expectedRanges, rangeHighlighters.map { it.textRange })
    assertEquals(expectedCount, rangeHighlighters.size)
    return rangeHighlighters
  }

  private fun assertHyperlinks(linkText: String, expectedCount: Int): List<RangeHighlighter> {
    val text = document.text
    val expectedRanges = text.allOccurrencesOf(linkText).map {
      TextRange(it, it + linkText.length)
    }.toList()
    val rangeHighlighters = collectAllHyperlinks()
    for (rangeHighlighter in rangeHighlighters) {
      assertEquals(linkText, rangeHighlighter.textRange.substring(text))
    }
    assertEquals(expectedRanges, rangeHighlighters.map { it.textRange })
    assertEquals(expectedCount, rangeHighlighters.size)
    return rangeHighlighters
  }

  private fun assertInlays(inlayText: String, expectedCount: Int): List<Inlay<*>> {
    val text = document.text
    val expectedOffsets = text.allOccurrencesOf(inlayText).map { it + inlayText.length }.toList()
    val inlays = collectAllInlays()
    val actualOffsets = inlays.map { it.offset }
    for (offset in actualOffsets) {
      assertEquals(inlayText, TextRange(offset - inlayText.length, offset).substring(text))
    }
    assertEquals(expectedOffsets, actualOffsets)
    assertEquals(expectedCount, inlays.size)
    return inlays
  }
}

private open class MyHyperlinkFilter(linkText: String, delay: Duration = 0.milliseconds) : MyFilter(linkText, delay) {
  override fun createResultItem(startOffset: Int, endOffset: Int): ResultItem {
    return ResultItem(startOffset, endOffset, MyHyperlinkInfo(linkText), null)
  }

  data class MyHyperlinkInfo(val linkText: String) : HyperlinkInfo {
    override fun navigate(project: Project) {}
  }
}

private open class MyHighlightingFilter(linkText: String) : MyFilter(linkText, 0.milliseconds) {
  override fun createResultItem(startOffset: Int, endOffset: Int): ResultItem {
    return ResultItem(startOffset, endOffset, null, ConsoleViewContentType.USER_INPUT.attributes)
  }
}

private class MyInlayFilter(linkText: String) : MyFilter(linkText, 0.milliseconds) {
  override fun createResultItem(startOffset: Int, endOffset: Int): ResultItem {
    return MyInlay(startOffset, endOffset)
  }

  private class MyInlay(highlightStartOffset: Int, highlightEndOffset: Int) : ResultItem(highlightStartOffset, highlightEndOffset, null),
                                                                              InlayProvider {
    override fun createInlay(editor: Editor, offset: Int): Inlay<*>? {
      return editor.inlayModel.addInlineElement(offset, createEmptyInlayRenderer())
    }
  }
}

private abstract class MyFilter(val linkText: String, val delay: Duration) : Filter {
  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val startInd = line.indexOf(linkText)
    // emulate real filter delay
    Thread.sleep(delay.inWholeMilliseconds)
    if (startInd == -1) return null
    val startOffset = entireLength - line.length + startInd
    val endOffset = startOffset + linkText.length
    return Filter.Result(listOf(createResultItem(if (emptyAtEndOffset) endOffset else startOffset, endOffset)))
  }

  open val emptyAtEndOffset: Boolean get() = false

  abstract fun createResultItem(startOffset: Int, endOffset: Int): ResultItem
}

private fun createEmptyInlayRenderer(): EditorCustomElementRenderer = EditorCustomElementRenderer { 42 }

private fun eternal(): Expirable = Expirable { false }

private val AWAIT_HYPERLINK_TIMEOUT: Duration = 30.seconds

private fun assertAllInvalid(rangeMarkers: List<RangeMarker>) {
  for (rangeMarker in rangeMarkers) {
    assertFalse(rangeMarker.isValid)
  }
}

private fun assertAllInlaysInvalid(inlays: List<Inlay<*>>) {
  for (inlay in inlays) {
    assertFalse(inlay.isValid)
  }
}

private fun assertValid(rangeMarker: RangeMarker) {
  assertTrue(rangeMarker.isValid)
}

private fun assertValid(inlay: Inlay<*>) {
  assertTrue(inlay.isValid)
}
