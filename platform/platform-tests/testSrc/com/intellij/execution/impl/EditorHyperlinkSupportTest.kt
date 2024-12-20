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
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Expirable
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EditorHyperlinkSupportTest : BasePlatformTestCase() {

  private lateinit var editor: EditorEx

  private val hyperlinkSupport: EditorHyperlinkSupport
    get() = EditorHyperlinkSupport.get(editor)

  private val document: Document
    get() = editor.document

  override fun setUp() {
    super.setUp()
    editor = createEditor()
  }

  private fun createEditor(): EditorEx {
    val editor = EditorFactory.getInstance().createEditor(DocumentImpl("", true), project) as EditorEx
    Disposer.register(testRootDisposable) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
    return editor
  }

  private fun collectAllHyperlinks(): List<RangeHighlighter> {
    return EditorHyperlinkSupport.getRangeHighlighters(0, document.textLength, true, false, editor)
  }

  private fun collectAllHighlightings(): List<RangeHighlighter> {
    return EditorHyperlinkSupport.getRangeHighlighters(0, document.textLength, false, true, editor)
  }

  private fun collectAllInlays(): List<Inlay<*>> {
    return hyperlinkSupport.getInlays(0, document.textLength)
  }

  fun `test custom hyperlinks survive clearing`() {
    val customLinkText = "custom-link"
    val standardLinkText = "standard-link"
    document.insertString(0, """
        $standardLinkText // line 0
        $customLinkText   // line 1
        $standardLinkText // line 2
    """.trimIndent())

    hyperlinkSupport.highlightHyperlinksLater(MyHyperlinkFilter(standardLinkText), 0, 2, createNotExpirable())
    val expectedLinksRanges = listOf(TextRange.from(0, standardLinkText.length),
                                     TextRange.from(document.getLineStartOffset(2), standardLinkText.length))
    assertEquals(expectedLinksRanges, collectAllHyperlinks().map { it.textRange })

    val customLinkStartOffset = document.getLineStartOffset(1)
    val customHyperlink = editor.getMarkupModel().addRangeHighlighter(CodeInsightColors.HYPERLINK_ATTRIBUTES,
                                                                                         customLinkStartOffset,
                                                                                         customLinkStartOffset + customLinkText.length,
                                                                                         HighlighterLayer.HYPERLINK,
                                                                                         HighlighterTargetArea.EXACT_RANGE)
    val hyperlinks = collectAllHyperlinks()
    assertEquals(expectedLinksRanges, hyperlinks.map { it.textRange })

    hyperlinkSupport.clearHyperlinks(0, document.textLength)

    assertEmpty(collectAllHyperlinks())
    hyperlinks.forEach { assertFalse(it.isValid) }
    assertTrue(customHyperlink.isValid)
  }

  fun `test custom highlightings survive clearing`() {
    val customHighlightingText = "custom-highlighting"
    val standardHighlightingText = "standard-highlighting"
    document.insertString(0, """
        $standardHighlightingText // line 0
        $customHighlightingText   // line 1
        $standardHighlightingText // line 2
    """.trimIndent())

    hyperlinkSupport.highlightHyperlinksLater(MyHighlightingFilter(standardHighlightingText), 0, 2, createNotExpirable())
    val expectedHighlightingsRanges = listOf(TextRange.from(0, standardHighlightingText.length),
                                             TextRange.from(document.getLineStartOffset(2), standardHighlightingText.length))
    assertEquals(expectedHighlightingsRanges, collectAllHighlightings().map { it.textRange })

    val customLinkStartOffset = document.getLineStartOffset(1)
    val customHighlighting = editor.getMarkupModel().addRangeHighlighter(customLinkStartOffset,
                                                                         customLinkStartOffset + customHighlightingText.length,
                                                                         HighlighterLayer.CONSOLE_FILTER,
                                                                         editor.colorsScheme.getAttributes(ConsoleHighlighter.GREEN),
                                                                         HighlighterTargetArea.EXACT_RANGE)
    val highlightings = collectAllHighlightings()
    assertEquals(expectedHighlightingsRanges, highlightings.map { it.textRange })

    hyperlinkSupport.clearHyperlinks(0, document.textLength)

    assertEmpty(collectAllHighlightings())
    highlightings.forEach { assertFalse(it.isValid) }
    assertTrue(customHighlighting.isValid)
  }

  fun `test custom inlays survive clearing`() {
    val customInlayText = "custom-inlay"
    val standardInlayText = "standard-inlay"
    document.insertString(0, """
        $standardInlayText // line 0
        $customInlayText   // line 1
        $standardInlayText // line 2
    """.trimIndent())

    hyperlinkSupport.highlightHyperlinksLater(MyInlayFilter(standardInlayText), 0, 2, createNotExpirable())
    val expectedInlaysOffsets = listOf(standardInlayText.length, document.getLineStartOffset(2) + standardInlayText.length)
    assertEquals(expectedInlaysOffsets, collectAllInlays().map { it.offset })

    val customInlay = editor.getInlayModel().addInlineElement<EditorCustomElementRenderer>(
      document.getLineStartOffset(1) + customInlayText.length, createEmptyInlayRenderer())!!

    val inlays = collectAllInlays()
    assertEquals(expectedInlaysOffsets, inlays.map { it.offset })

    hyperlinkSupport.clearHyperlinks(0, document.textLength)

    assertEmpty(collectAllInlays())
    inlays.forEach { assertFalse(it.isValid) }
    assertTrue(customInlay.isValid)
  }

  fun `test clearing adjacent links separated by space`() {
    val fooLinkText = "foo"
    val barLinkText = "bar"
    document.insertString(0, "$fooLinkText $barLinkText")
    val compositeFilter: CompositeFilter = CompositeFilter(project).also {
      it.addFilter(MyHyperlinkFilter(fooLinkText))
      it.addFilter(MyHyperlinkFilter(barLinkText))
      it.setForceUseAllFilters(true)
    }
    hyperlinkSupport.highlightHyperlinksLater(compositeFilter, 0, 0, createNotExpirable())
    val expectedFooLinkRange = TextRange.from(0, fooLinkText.length)
    val expectedBarLinkRange = TextRange.from(fooLinkText.length + 1, barLinkText.length)
    assertEquals(listOf(expectedFooLinkRange, expectedBarLinkRange), collectAllHyperlinks().map { it.textRange })

    hyperlinkSupport.clearHyperlinks(expectedFooLinkRange.startOffset, expectedFooLinkRange.endOffset)
    assertEquals(listOf(expectedBarLinkRange), collectAllHyperlinks().map { it.textRange })

    hyperlinkSupport.clearHyperlinks(expectedBarLinkRange.startOffset, expectedBarLinkRange.startOffset)
    assertEmpty(collectAllHyperlinks())
  }

  fun `test clearing adjacent links`() {
    val fooLinkText = "foo"
    val barLinkText = "bar"
    document.insertString(0, "$fooLinkText$barLinkText")
    val compositeFilter = CompositeFilter(project).also {
      it.addFilter(MyHyperlinkFilter(fooLinkText))
      it.addFilter(MyHyperlinkFilter(barLinkText))
      it.setForceUseAllFilters(true)
    }
    hyperlinkSupport.highlightHyperlinksLater(compositeFilter, 0, 0, createNotExpirable())
    val expectedFooLinkRange = TextRange.from(0, fooLinkText.length)
    val expectedBarLinkRange = TextRange.from(fooLinkText.length, barLinkText.length)
    assertEquals(listOf(expectedFooLinkRange, expectedBarLinkRange), collectAllHyperlinks().map { it.textRange })

    // TODO make endOffset exclusive for hyperlinks/highlightings to avoid removing adjacent links, like it's happening here:
    hyperlinkSupport.clearHyperlinks(expectedFooLinkRange.startOffset, expectedFooLinkRange.endOffset)
    assertEmpty(collectAllHyperlinks())
  }

  fun `test inclusive endOffset`() {
    val fooLinkText = "foo"
    document.insertString(0, fooLinkText)

    val expectedFooRange = TextRange.from(0, fooLinkText.length)
    hyperlinkSupport.highlightHyperlinksLater(MyHyperlinkFilter(fooLinkText), 0, 0, createNotExpirable())
    assertEquals(listOf(expectedFooRange), collectAllHyperlinks().map { it.textRange })
    hyperlinkSupport.clearHyperlinks(expectedFooRange.endOffset, expectedFooRange.endOffset)
    assertEmpty(collectAllHyperlinks())

    hyperlinkSupport.highlightHyperlinksLater(MyHighlightingFilter(fooLinkText), 0, 0, createNotExpirable())
    assertEquals(listOf(expectedFooRange), collectAllHighlightings().map { it.textRange })
    hyperlinkSupport.clearHyperlinks(expectedFooRange.endOffset, expectedFooRange.endOffset)
    assertEmpty(collectAllHighlightings())

    hyperlinkSupport.highlightHyperlinksLater(MyInlayFilter(fooLinkText), 0, 0, createNotExpirable())
    assertEquals(listOf(expectedFooRange).map { it.endOffset }, collectAllInlays().map { it.offset })
    hyperlinkSupport.clearHyperlinks(expectedFooRange.endOffset, expectedFooRange.endOffset)
    assertEmpty(collectAllInlays())
  }

  // TODO ban empty hyperlinks and update the test
  fun `test empty link`() {
    val fooLinkText = "foo"
    document.insertString(0, fooLinkText)

    val expectedFooRange = TextRange.from(fooLinkText.length, 0)
    hyperlinkSupport.highlightHyperlinksLater(object: MyHyperlinkFilter(fooLinkText) {
      override val emptyAtEndOffset: Boolean get() = true
    }, 0, 0, createNotExpirable())
    assertEquals(listOf(expectedFooRange), collectAllHyperlinks().map { it.textRange })
    hyperlinkSupport.clearHyperlinks(expectedFooRange.endOffset, expectedFooRange.endOffset)
    assertEmpty(collectAllHyperlinks())
  }

  // TODO ban empty highlightings and update the test
  fun `test empty highlighting`() {
    val fooLinkText = "foo"
    document.insertString(0, fooLinkText)

    val expectedFooRange = TextRange.from(fooLinkText.length, 0)
    hyperlinkSupport.highlightHyperlinksLater(object: MyHighlightingFilter(fooLinkText) {
      override val emptyAtEndOffset: Boolean get() = true
    }, 0, 0, createNotExpirable())
    assertEquals(listOf(expectedFooRange), collectAllHighlightings().map { it.textRange })
    hyperlinkSupport.clearHyperlinks(expectedFooRange.endOffset, expectedFooRange.endOffset)
    assertEmpty(collectAllHighlightings())
  }
}

private open class MyHyperlinkFilter(linkText: String) : MyFilter(linkText) {
  override fun createResultItem(startOffset: Int, endOffset: Int): ResultItem {
    return ResultItem(startOffset, endOffset, MyHyperlinkInfo, null)
  }

  object MyHyperlinkInfo : HyperlinkInfo {
    override fun navigate(project: Project) {}
  }
}

private open class MyHighlightingFilter(linkText: String) : MyFilter(linkText) {
  override fun createResultItem(startOffset: Int, endOffset: Int): ResultItem {
    return ResultItem(startOffset, endOffset, null, ConsoleViewContentType.USER_INPUT.attributes)
  }
}

private class MyInlayFilter(linkText: String) : MyFilter(linkText) {
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

private abstract class MyFilter(val linkText: String) : Filter {
  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val startInd = line.indexOf(linkText)
    if (startInd == -1) return null
    val startOffset = entireLength - line.length + startInd
    val endOffset = startOffset + linkText.length
    return Filter.Result(listOf(createResultItem(if (emptyAtEndOffset) endOffset else startOffset, endOffset)))
  }

  open val emptyAtEndOffset: Boolean get() = false

  abstract fun createResultItem(startOffset: Int, endOffset: Int): ResultItem
}

private fun createEmptyInlayRenderer(): EditorCustomElementRenderer = EditorCustomElementRenderer { 42 }

private fun createNotExpirable(): Expirable = Expirable { false }
