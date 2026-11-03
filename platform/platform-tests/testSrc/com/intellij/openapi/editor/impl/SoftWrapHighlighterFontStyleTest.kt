// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.lang.Language
import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.impl.softwrap.mapping.IncrementalCacheUpdateEvent
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapParsingListener
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.MockFontLayoutService
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch

private const val TEXT = "ab cd ef"
private const val WRAP_COLUMN = 10
private const val BOLD_CHAR_WIDTH = 2 * EditorModelFixture.TEST_CHAR_WIDTH
private const val STALE_SOFT_WRAPS = "The soft wraps must match a full recalculation"
private const val COMMENT_START = "/*"
private const val MANY_LINES = 100
private val MANY_LINES_TEXT = List(MANY_LINES) { TEXT }.joinToString("\n")

private val COMMENT_TOKEN = IElementType("SOFT_WRAP_TEST_COMMENT", Language.ANY)
private val TEXT_TOKEN = IElementType("SOFT_WRAP_TEST_TEXT", Language.ANY)

/**
 * Checks that a font style change recalculates the soft wraps of its range and updates the editor height.
 * The change comes from a range highlighter, or from the editor highlighter when it lexes the text after an edit again.
 * The highlighter changes of one EDT event recalculate the soft wraps once, in a later EDT event.
 *
 * [BoldWidthFontLayoutService] makes a bold character two times as wide as a plain character.
 * So [TEXT] fits in one line at [WRAP_COLUMN] in the plain style, and it does not fit in the bold style.
 */
@TestApplication
class SoftWrapHighlighterFontStyleTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  @Test
  fun testAddedBoldHighlighterAddsSoftWrap(): Unit = fixture.runTest {
    initBoldWidthText()
    configureSoftWraps(WRAP_COLUMN)
    assertTrue(softWrapOffsets().isEmpty(), "The plain text must fit in one line")

    addHighlighter(boldAttributes())
    applyPendingChanges()

    val offsets = softWrapOffsets()
    val expected = recalculatedSoftWrapOffsets()
    assertFalse(expected.isEmpty(), "The bold text must not fit in one line")
    assertEquals(expected, offsets, STALE_SOFT_WRAPS)
  }

  @Test
  fun testRemovedBoldHighlighterRemovesSoftWrap(): Unit = fixture.runTest {
    initBoldWidthText()
    val highlighter = addHighlighter(boldAttributes())
    configureSoftWraps(WRAP_COLUMN)
    assertFalse(softWrapOffsets().isEmpty(), "The bold text must not fit in one line")

    highlighter.dispose()
    applyPendingChanges()

    val offsets = softWrapOffsets()
    assertEquals(recalculatedSoftWrapOffsets(), offsets, STALE_SOFT_WRAPS)
  }

  @Test
  fun testBoldAttributesChangeAddsSoftWrap(): Unit = fixture.runTest {
    initBoldWidthText()
    val highlighter = addHighlighter(TextAttributes())
    configureSoftWraps(WRAP_COLUMN)
    assertTrue(softWrapOffsets().isEmpty(), "The plain text must fit in one line")

    (highlighter as RangeHighlighterEx).setTextAttributes(boldAttributes())
    applyPendingChanges()

    val offsets = softWrapOffsets()
    val expected = recalculatedSoftWrapOffsets()
    assertFalse(expected.isEmpty(), "The bold text must not fit in one line")
    assertEquals(expected, offsets, STALE_SOFT_WRAPS)
  }

  @Test
  fun testAddedSoftWrapUpdatesEditorHeight(): Unit = fixture.runTest {
    initBoldWidthText()
    configureSoftWraps(WRAP_COLUMN)
    val contentComponent = editor.contentComponent
    val oneLineHeight = contentComponent.height
    assertEquals(contentComponent.preferredSize.height, oneLineHeight, "The editor height must match its preferred height")

    addHighlighter(boldAttributes())
    applyPendingChanges()

    assertTrue(contentComponent.preferredSize.height > oneLineHeight, "The new soft wrap must add a visual line")
    assertEquals(contentComponent.preferredSize.height, contentComponent.height, "The editor height must follow the soft wraps")
  }

  @Test
  fun testBoldHighlighterWithoutSoftWrapsAppliesAtOnce(): Unit = fixture.runTest {
    initBoldWidthText()
    val contentComponent = editor.contentComponent
    val plainWidth = contentComponent.preferredSize.width

    // Without soft wraps, a change on the EDT takes the fast path, so no EDT event must run first
    addHighlighter(boldAttributes())

    assertTrue(contentComponent.preferredSize.width > plainWidth, "The bold text must widen the editor at once")
  }

  @Test
  fun testBoldHighlighterAddedOnBackgroundThreadAddsSoftWrap(): Unit = fixture.runTest {
    initBoldWidthText()
    configureSoftWraps(WRAP_COLUMN)
    assertTrue(softWrapOffsets().isEmpty(), "The plain text must fit in one line")

    // As the daemon does, the background thread adds the highlighter to the document markup model in a read action
    val markupModel = documentMarkupModel()
    onBackgroundThread {
      addHighlighter(
        0,
        editor.document.textLength,
        boldAttributes(),
        markupModel,
      )
    }
    applyPendingChanges()

    val offsets = softWrapOffsets()
    val expected = recalculatedSoftWrapOffsets()
    assertFalse(expected.isEmpty(), "The bold text must not fit in one line")
    assertEquals(expected, offsets, STALE_SOFT_WRAPS)
  }

  @Test
  fun testBoldHighlightersAddedOnSeveralThreadsAddSoftWraps(): Unit = fixture.runTest {
    initBoldWidthText(MANY_LINES_TEXT)
    configureSoftWraps(WRAP_COLUMN)
    val document = editor.document
    val markupModel = documentMarkupModel()
    val threadCount = 4
    val start = CountDownLatch(1)
    val tasks = (0 until threadCount).map { firstLine ->
      ApplicationManager.getApplication().executeOnPooledThread(Runnable {
        start.await()
        for (line in firstLine until MANY_LINES step threadCount) {
          runReadActionBlocking {
            addHighlighter(
              document.getLineStartOffset(line),
              document.getLineEndOffset(line),
              boldAttributes(),
              markupModel,
            )
          }
        }
      })
    }

    // The threads start together, so their requests can join while the EDT applies an earlier one
    start.countDown()
    PlatformTestUtil.waitWithEventsDispatching("The threads must add their highlighters", { tasks.all { it.isDone } }, 10)
    tasks.forEach { it.get() }
    applyPendingChanges()

    val offsets = softWrapOffsets()
    val expected = recalculatedSoftWrapOffsets()
    assertTrue(expected.size >= MANY_LINES, "Each bold line must get a soft wrap")
    assertEquals(expected, offsets, STALE_SOFT_WRAPS)
  }

  @Test
  fun testBoldHighlighterAddedInInlayBatchAddsSoftWrap(): Unit = fixture.runTest {
    initBoldWidthText()
    configureSoftWraps(WRAP_COLUMN)
    assertTrue(softWrapOffsets().isEmpty(), "The plain text must fit in one line")

    editor.inlayModel.execute(true) {
      addHighlighter(boldAttributes())
    }

    val offsets = softWrapOffsets()
    val expected = recalculatedSoftWrapOffsets()
    assertFalse(expected.isEmpty(), "The bold text must not fit in one line")
    assertEquals(expected, offsets, STALE_SOFT_WRAPS)
  }

  @Test
  fun testBoldHighlighterAddedInDocumentChangeAddsSoftWrap(): Unit = fixture.runTest {
    initBoldWidthText("x\n$TEXT")
    configureSoftWraps(WRAP_COLUMN)
    assertTrue(softWrapOffsets().isEmpty(), "The plain text must fit in one line")
    val document = editor.document
    document.addDocumentListener(object : PrioritizedDocumentListener {
      // The editor handles the document change after this listener
      override fun getPriority(): Int = EditorDocumentPriorities.EDITOR_DOCUMENT_ADAPTER - 1

      override fun documentChanged(event: DocumentEvent) {
        addHighlighter(document.getLineStartOffset(1), document.textLength, boldAttributes())
      }
    }, testRootDisposable)

    runWriteCommand { document.insertString(0, "y") }

    val offsets = softWrapOffsets()
    val expected = recalculatedSoftWrapOffsets()
    assertFalse(expected.isEmpty(), "The bold text must not fit in one line")
    assertEquals(expected, offsets, STALE_SOFT_WRAPS)
  }

  @Test
  fun testInsertionBeforePendingRequestMovesItsArea(): Unit = fixture.runTest {
    // The lexer of this editor lexes only the changed lines again, so the insertion does not recalculate the bold line
    initPlainCommentText("a\nb\n$TEXT")
    configureSoftWraps(WRAP_COLUMN)
    val document = editor.document
    addHighlighter(document.getLineStartOffset(2), document.textLength, boldAttributes())
    val recalculationStarts = recordRecalculationStarts()

    // The request of the bold highlighter is pending until its coroutine runs, and the insertion moves the bold line down
    runWriteCommand { document.insertString(0, "x\n".repeat(10)) }
    applyPendingChanges()

    val boldLineStart = document.getLineStartOffset(12)
    assertTrue(recalculationStarts.contains(boldLineStart)) {
      "The pending request must recalculate the moved bold line, but the recalculations started at $recalculationStarts"
    }
    assertEquals(recalculatedSoftWrapOffsets(), softWrapOffsets(), STALE_SOFT_WRAPS)
  }

  @Test
  fun testDeletionOfPendingRequestTextKeepsItsAreaInText(): Unit = fixture.runTest {
    initBoldWidthText("x\n$TEXT")
    configureSoftWraps(WRAP_COLUMN)
    val document = editor.document
    addHighlighter(document.getLineStartOffset(1), document.textLength, boldAttributes())

    // The request of the bold highlighter is pending until its coroutine runs, and the deletion removes all text of its area
    runWriteCommand { document.deleteString(0, document.textLength) }
    applyPendingChanges()

    assertEquals(recalculatedSoftWrapOffsets(), softWrapOffsets(), STALE_SOFT_WRAPS)
  }

  @Test
  fun testColorChangeDoesNotWidenSoftWrapRecalculation(): Unit = fixture.runTest {
    initBoldWidthText("ab\ncd\n$TEXT")
    configureSoftWraps(WRAP_COLUMN)
    val document = editor.document
    val textStart = document.getLineStartOffset(2)
    val recalculationStarts = recordRecalculationStarts()

    // The inlay batch defers both changes, so the editor applies them together
    editor.inlayModel.execute(true) {
      addHighlighter(TextAttributes(Color.RED, null, null, null, Font.PLAIN))
      addHighlighter(textStart, document.textLength, boldAttributes())
    }

    assertFalse(recalculationStarts.isEmpty(), "The bold highlighter must recalculate the soft wraps")
    assertTrue(recalculationStarts.all { it >= textStart }) {
      "Only the bold text needs new soft wraps, but the recalculations started at $recalculationStarts"
    }
    assertEquals(recalculatedSoftWrapOffsets(), softWrapOffsets(), STALE_SOFT_WRAPS)
  }

  @Test
  fun testBoldHighlightersAddedInOneEventRecalculateSoftWrapsOnce(): Unit = fixture.runTest {
    initBoldWidthText(MANY_LINES_TEXT)
    configureSoftWraps(WRAP_COLUMN)
    applyPendingChanges()
    val recalculationStarts = recordRecalculationStarts()

    addBoldHighlighterToEachLine()
    applyPendingChanges()

    assertEquals(1, recalculationStarts.size, "The bold highlighters of one EDT event must recalculate the soft wraps once")
    assertEquals(recalculatedSoftWrapOffsets(), softWrapOffsets(), STALE_SOFT_WRAPS)
  }

  @Test
  fun testBoldHighlightersReplacedInOneEventRecalculateSoftWrapsOnce(): Unit = fixture.runTest {
    initBoldWidthText(MANY_LINES_TEXT)
    configureSoftWraps(WRAP_COLUMN)
    addBoldHighlighterToEachLine()
    applyPendingChanges()
    val recalculationStarts = recordRecalculationStarts()

    // A markup update of the unified diff viewer removes all highlighters and adds them again in one EDT event
    editor.markupModel.removeAllHighlighters()
    addBoldHighlighterToEachLine()
    applyPendingChanges()

    assertEquals(1, recalculationStarts.size, "The replaced bold highlighters must recalculate the soft wraps once")
    assertEquals(recalculatedSoftWrapOffsets(), softWrapOffsets(), STALE_SOFT_WRAPS)
  }

  @Test
  fun testBoldHighlightersReplacedAroundTextChangeRecalculateSoftWrapsOnce(): Unit = fixture.runTest {
    initBoldWidthText(MANY_LINES_TEXT)
    configureSoftWraps(WRAP_COLUMN)
    addBoldHighlighterToEachLine()
    applyPendingChanges()
    val document = editor.document
    val lastLineStart = document.getLineStartOffset(document.lineCount - 1)
    val recalculationStarts = recordRecalculationStarts()

    // A rediff of the unified diff viewer removes all highlighters, changes the text, and adds the highlighters again
    editor.markupModel.removeAllHighlighters()
    runWriteCommand { document.replaceString(lastLineStart, document.textLength, TEXT.reversed()) }
    addBoldHighlighterToEachLine()
    applyPendingChanges()

    // The text change recalculates only its line, so a recalculation from offset 0 comes from the bold highlighters
    assertEquals(1, recalculationStarts.count { it == 0 }) {
      "The replaced bold highlighters must recalculate the soft wraps once, but the recalculations started at $recalculationStarts"
    }
    assertEquals(recalculatedSoftWrapOffsets(), softWrapOffsets(), STALE_SOFT_WRAPS)
  }

  @Test
  fun testNewEditorHighlighterAddsSoftWrap(): Unit = fixture.runTest {
    initBoldWidthText("${COMMENT_START}x\n$TEXT")
    configureSoftWraps(WRAP_COLUMN)
    assertTrue(softWrapOffsets().isEmpty(), "The plain text must fit in one line")

    setBoldCommentHighlighter()

    val offsets = softWrapOffsets()
    val expected = recalculatedSoftWrapOffsets()
    assertFalse(expected.isEmpty(), "The bold comment must not fit in one line")
    assertEquals(expected, offsets, STALE_SOFT_WRAPS)
  }

  @Test
  fun testNewColorsSchemeAddsSoftWrap(): Unit = fixture.runTest {
    initPlainCommentText("${COMMENT_START}x\n$TEXT")
    configureSoftWraps(WRAP_COLUMN)
    assertTrue(softWrapOffsets().isEmpty(), "The plain comment must fit in one line")
    val scheme = EditorColorsManager.getInstance().globalScheme.clone() as EditorColorsScheme
    scheme.setAttributes(DefaultLanguageHighlighterColors.BLOCK_COMMENT, boldAttributes())

    (editor as EditorEx).colorsScheme = scheme

    val offsets = softWrapOffsets()
    val expected = recalculatedSoftWrapOffsets()
    assertFalse(expected.isEmpty(), "The bold comment must not fit in one line")
    assertEquals(expected, offsets, STALE_SOFT_WRAPS)
  }

  @Test
  fun testGlobalSchemeChangeAddsSoftWrap(): Unit = fixture.runTest {
    initPlainCommentText("${COMMENT_START}x\n$TEXT")
    configureSoftWraps(WRAP_COLUMN)
    assertTrue(softWrapOffsets().isEmpty(), "The plain comment must fit in one line")
    // The scheme of this editor stands in for the global scheme, so the test changes no global state
    editor.colorsScheme.setAttributes(DefaultLanguageHighlighterColors.BLOCK_COMMENT, boldAttributes())

    ApplicationManager.getApplication().messageBus.syncPublisher(EditorColorsManager.TOPIC).globalSchemeChange(null)

    val offsets = softWrapOffsets()
    val expected = recalculatedSoftWrapOffsets()
    assertFalse(expected.isEmpty(), "The bold comment must not fit in one line")
    assertEquals(expected, offsets, STALE_SOFT_WRAPS)
  }

  @Test
  fun testCommentStartAddsSoftWrapToLaterLine(): Unit = fixture.runTest {
    initBoldCommentText("x\n$TEXT")
    configureSoftWraps(WRAP_COLUMN)
    assertTrue(softWrapOffsets().isEmpty(), "The plain text must fit in one line")

    runWriteCommand { editor.document.insertString(0, COMMENT_START) }

    val offsets = softWrapOffsets()
    val expected = recalculatedSoftWrapOffsets()
    assertFalse(expected.isEmpty(), "The bold comment must not fit in one line")
    assertEquals(expected, offsets, STALE_SOFT_WRAPS)
  }

  @Test
  fun testCommentStartRemovalRemovesSoftWrapFromLaterLine(): Unit = fixture.runTest {
    initBoldCommentText("${COMMENT_START}x\n$TEXT")
    configureSoftWraps(WRAP_COLUMN)
    assertFalse(softWrapOffsets().isEmpty(), "The bold comment must not fit in one line")

    runWriteCommand { editor.document.deleteString(0, COMMENT_START.length) }

    val offsets = softWrapOffsets()
    assertEquals(recalculatedSoftWrapOffsets(), offsets, STALE_SOFT_WRAPS)
  }

  @Test
  fun testCommentStartUpdatesEditorHeight(): Unit = fixture.runTest {
    initBoldCommentText("x\n$TEXT")
    configureSoftWraps(WRAP_COLUMN)
    val contentComponent = editor.contentComponent
    val twoLinesHeight = contentComponent.height
    assertEquals(contentComponent.preferredSize.height, twoLinesHeight, "The editor height must match its preferred height")

    runWriteCommand { editor.document.insertString(0, COMMENT_START) }

    assertTrue(contentComponent.preferredSize.height > twoLinesHeight, "The new soft wrap must add a visual line")
    assertEquals(contentComponent.preferredSize.height, contentComponent.height, "The editor height must follow the soft wraps")
  }
}

/**
 * Sets [BoldWidthFontLayoutService] and opens a plain text file with [text].
 * The fixture resets the font layout service after the test.
 */
private fun EditorModelFixture.initBoldWidthText(text: String = TEXT) {
  FontLayoutService.setInstance(BoldWidthFontLayoutService())
  initText(text)
}

/**
 * Opens a plain text file with [text] and gives the editor a highlighter with [CommentToEndLexer].
 * The comment key is bold in the scheme of this editor only.
 */
private fun EditorModelFixture.initBoldCommentText(text: String) {
  initBoldWidthText(text)
  setBoldCommentHighlighter()
}

/**
 * Opens a plain text file with [text] and gives the editor a highlighter with [CommentToEndLexer].
 * The comment key is plain in the scheme of this editor only.
 */
private fun EditorModelFixture.initPlainCommentText(text: String) {
  initBoldWidthText(text)
  setCommentHighlighter(TextAttributes())
}

/**
 * Gives the editor a highlighter with [CommentToEndLexer]. The comment key is bold in the scheme of this editor only.
 */
private fun EditorModelFixture.setBoldCommentHighlighter() {
  setCommentHighlighter(boldAttributes())
}

private fun EditorModelFixture.setCommentHighlighter(commentAttributes: TextAttributes) {
  val editor = editor as EditorEx
  editor.colorsScheme.setAttributes(DefaultLanguageHighlighterColors.BLOCK_COMMENT, commentAttributes)
  editor.highlighter = LexerEditorHighlighter(CommentToEndHighlighter(), editor.colorsScheme)
}

private fun EditorModelFixture.addHighlighter(attributes: TextAttributes): RangeHighlighter {
  return addHighlighter(0, editor.document.textLength, attributes)
}

/**
 * Adds a highlighter to [markupModel]. By default, it is the markup model of the editor.
 */
private fun EditorModelFixture.addHighlighter(
  startOffset: Int,
  endOffset: Int,
  attributes: TextAttributes,
  markupModel: MarkupModel = editor.markupModel,
): RangeHighlighter {
  return markupModel.addRangeHighlighter(
    /* startOffset = */ startOffset,
    /* endOffset = */ endOffset,
    /* layer = */ HighlighterLayer.SYNTAX,
    /* textAttributes = */ attributes,
    /* targetArea = */ HighlighterTargetArea.EXACT_RANGE,
  )
}

/**
 * Adds a bold highlighter to each line of the editor, one highlighter after the other.
 */
private fun EditorModelFixture.addBoldHighlighterToEachLine() {
  val document = editor.document
  for (line in 0 until document.lineCount) {
    addHighlighter(document.getLineStartOffset(line), document.getLineEndOffset(line), boldAttributes())
  }
}

/**
 * Records the start offset of each soft wrap recalculation in the editor.
 * The returned list grows with each later recalculation.
 */
private fun EditorModelFixture.recordRecalculationStarts(): List<Int> {
  val recalculationStarts = ArrayList<Int>()
  (editor.softWrapModel as SoftWrapModelImpl).addSoftWrapParsingListener(object : SoftWrapParsingListener {
    override fun onRegionReparseStart(event: IncrementalCacheUpdateEvent) {
      recalculationStarts.add(event.startOffset)
    }
  })
  return recalculationStarts
}

/**
 * The markup model of the document of the editor. The daemon adds its highlighters to this model.
 */
private fun EditorModelFixture.documentMarkupModel(): MarkupModel {
  return DocumentMarkupModel.forDocument(editor.document, editor.project, true)
}

/**
 * Runs [action] in a read action on a background thread and waits for its result.
 */
private fun <T> onBackgroundThread(action: () -> T): T {
  val result = ApplicationManager.getApplication().executeOnPooledThread(Callable {
    runReadActionBlocking(action)
  })
  return result.get()
}

/**
 * Runs the pending EDT events, so the editor applies the highlighter changes that are pending until a later EDT event.
 */
private fun applyPendingChanges() {
  PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
}

private fun EditorModelFixture.softWrapOffsets(): List<Int> {
  return editor.softWrapModel.getSoftWrapsForRange(0, editor.document.textLength).map { it.start }
}

/**
 * Recalculates all soft wraps of the editor and returns their offsets.
 */
private fun EditorModelFixture.recalculatedSoftWrapOffsets(): List<Int> {
  val applianceManager = (editor.softWrapModel as SoftWrapModelImpl).applianceManager
  applianceManager.reset()
  applianceManager.recalculateIfNecessary("SoftWrapHighlighterFontStyleTest")
  return softWrapOffsets()
}

private fun boldAttributes(): TextAttributes {
  return TextAttributes(null, null, null, null, Font.BOLD)
}

/**
 * Highlights the tokens of [CommentToEndLexer] with the block comment key.
 */
private class CommentToEndHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer = CommentToEndLexer()

  override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
    return if (tokenType == COMMENT_TOKEN) {
      arrayOf(DefaultLanguageHighlighterColors.BLOCK_COMMENT)
    } else {
      TextAttributesKey.EMPTY_ARRAY
    }
  }
}

/**
 * Lexes [COMMENT_START] and all text after it as one [COMMENT_TOKEN].
 * Other text is one [TEXT_TOKEN] for each line, together with its line feed.
 * The lexer has one state, so the editor highlighter can restart it at each token.
 */
private class CommentToEndLexer : LexerBase() {
  private var buffer: CharSequence = ""
  private var bufferEnd = 0
  private var tokenStart = 0
  private var tokenEnd = 0
  private var tokenType: IElementType? = null

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    this.buffer = buffer
    bufferEnd = endOffset
    tokenEnd = startOffset
    advance()
  }

  override fun getState(): Int = 0

  override fun getTokenType(): IElementType? = tokenType

  override fun getTokenStart(): Int = tokenStart

  override fun getTokenEnd(): Int = tokenEnd

  override fun advance() {
    tokenStart = tokenEnd
    if (tokenStart >= bufferEnd) {
      tokenType = null
      return
    }
    if (isCommentStart(tokenStart)) {
      tokenType = COMMENT_TOKEN
      tokenEnd = bufferEnd
      return
    }
    tokenType = TEXT_TOKEN
    var end = tokenStart
    while (end < bufferEnd && buffer[end] != '\n' && !isCommentStart(end)) {
      end++
    }
    if (end < bufferEnd && buffer[end] == '\n') {
      end++
    }
    tokenEnd = end
  }

  override fun getBufferSequence(): CharSequence = buffer

  override fun getBufferEnd(): Int = bufferEnd

  private fun isCommentStart(offset: Int): Boolean {
    return offset + COMMENT_START.length <= bufferEnd && buffer.startsWith(COMMENT_START, offset)
  }
}

/**
 * Gives a bold character the width [BOLD_CHAR_WIDTH].
 * Every other character gets the width of [MockFontLayoutService].
 */
private class BoldWidthFontLayoutService : MockFontLayoutService(
  EditorModelFixture.TEST_CHAR_WIDTH,
  EditorModelFixture.TEST_LINE_HEIGHT,
  EditorModelFixture.TEST_DESCENT,
) {
  override fun charWidth(fontMetrics: FontMetrics, c: Char): Int = width(fontMetrics)

  override fun charWidth(fontMetrics: FontMetrics, codePoint: Int): Int = width(fontMetrics)

  override fun charWidth2D(fontMetrics: FontMetrics, codePoint: Int): Float = width(fontMetrics).toFloat()

  private fun width(fontMetrics: FontMetrics): Int {
    return if (fontMetrics.font.isBold) {
      BOLD_CHAR_WIDTH
    } else {
      EditorModelFixture.TEST_CHAR_WIDTH
    }
  }
}
