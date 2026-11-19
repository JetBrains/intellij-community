// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.util

import com.intellij.lang.Language
import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.highlighter.HighlighterClient
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.random.Random

@TestApplication
internal class LayeredLexerEditorHighlighterTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @ParameterizedTest
  @ValueSource(strings = ["", "|", "\n\n"])
  fun `unchanged mappings survive token insertion and removal`(separator: String): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val document = DocumentImpl("aa|bb|cc|dd", true)
      val highlighter = highlighter(document, separator)
      val original = markers(highlighter)
      runWriteAction { document.insertString(3, "xx|") }
      assertThat(markers(highlighter)).containsAll(original)
      assertConsistent(highlighter, document, separator)
      runWriteAction { document.deleteString(3, 6) }
      assertThat(markers(highlighter)).containsExactlyElementsOf(original)
      assertConsistent(highlighter, document, separator)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["", "|", "\n\n"])
  fun `changed text and layers update highlighting`(separator: String): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val document = DocumentImpl("aa|bb|aa|BB|cc", true)
      val highlighter = highlighter(document, separator)
      for (replacement in listOf("ee", "EE", "|", "aa|BB|aa", "")) {
        runWriteAction { document.replaceString(3, 5.coerceAtMost(document.textLength), replacement) }
        assertConsistent(highlighter, document, separator)
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["", "|", "\n\n"])
  fun `successive edits match fresh highlighting`(separator: String): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val document = DocumentImpl("aa|BB|aa|cc|DD", true)
      val highlighter = highlighter(document, separator)
      val random = Random(42)
      repeat(100) {
        val start = random.nextInt(document.textLength + 1)
        val end = random.nextInt(start, document.textLength + 1)
        val replacement = List(random.nextInt(8)) { "abAE|"[random.nextInt(5)] }.joinToString("")
        runWriteAction { document.replaceString(start, end, replacement) }
        assertConsistent(highlighter, document, separator)
      }
    }
  }

  private fun highlighter(document: Document, separator: String): LayeredLexerEditorHighlighter {
    val highlighter = LayeredLexerEditorHighlighter(TestSyntaxHighlighter(true), EditorColorsManager.getInstance().globalScheme)
    highlighter.registerLayer(LOWER, LayerDescriptor(TestSyntaxHighlighter(false), separator))
    highlighter.registerLayer(UPPER, LayerDescriptor(TestSyntaxHighlighter(false), separator))
    highlighter.setEditor(object : HighlighterClient {
      override fun getProject(): Project? = null
      override fun getDocument(): Document = document
      override fun repaint(start: Int, end: Int) {}
    })
    highlighter.setText(document.immutableCharSequence)
    document.addDocumentListener(highlighter, disposable)
    return highlighter
  }

  private fun markers(highlighter: LayeredLexerEditorHighlighter): List<RangeMarker> {
    val segments = highlighter.segments
    val field = segments.javaClass.getDeclaredField("myRanges").apply { isAccessible = true }
    return (field.get(segments) as Array<*>).take(segments.segmentCount).filterNotNull().map {
      it.javaClass.getDeclaredField("range").apply { isAccessible = true }.get(it) as RangeMarker
    }
  }

  private fun assertConsistent(highlighter: LayeredLexerEditorHighlighter, document: Document, separator: String) {
    val freshDocument = DocumentImpl(document.text, true)
    assertThat(tokens(highlighter)).isEqualTo(tokens(highlighter(freshDocument, separator)))
    val segments = highlighter.segments
    val expectedText = (0 until segments.segmentCount).filter {
      segments.unpackTokenFromData(segments.getSegmentData(it)) in listOf(LOWER, UPPER)
    }.map { document.charsSequence.subSequence(segments.getSegmentStart(it), segments.getSegmentEnd(it)).toString() }
    val actualText = markers(highlighter).map {
      assertThat(it.isValid).isTrue()
      it.document.charsSequence.subSequence(it.startOffset, it.endOffset).toString()
    }
    assertThat(actualText).isEqualTo(expectedText)
  }

  private fun tokens(highlighter: LayeredLexerEditorHighlighter): List<Triple<Int, Int, IElementType>> {
    val iterator = highlighter.createIterator(0)
    return buildList {
      while (!iterator.atEnd()) {
        add(Triple(iterator.start, iterator.end, iterator.tokenType))
        iterator.advance()
      }
    }
  }

  private class TestSyntaxHighlighter(private val outer: Boolean) : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = TestLexer(outer)
    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = TextAttributesKey.EMPTY_ARRAY
  }

  private class TestLexer(private val outer: Boolean) : LexerBase() {
    private var text: CharSequence = ""
    private var limit: Int = 0
    private var start: Int = 0
    private var end: Int = 0

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
      text = buffer
      limit = endOffset
      start = startOffset
      locateEnd()
    }

    override fun getState(): Int = if (start == 0) 0 else 1
    override fun getTokenType(): IElementType? = if (start == limit) null else typeAt(start)
    override fun getTokenStart(): Int = start
    override fun getTokenEnd(): Int = end
    override fun getBufferSequence(): CharSequence = text
    override fun getBufferEnd(): Int = limit

    override fun advance() {
      start = end
      locateEnd()
    }

    private fun locateEnd() {
      end = start
      if (end == limit) return
      end++
      if (outer) {
        while (end < limit && typeAt(end) == typeAt(start)) end++
      }
    }

    private fun typeAt(index: Int): IElementType = when {
      !outer -> if (text[index].lowercaseChar() in "aeiou") VOWEL else OTHER
      text[index].isLowerCase() -> LOWER
      text[index].isUpperCase() -> UPPER
      else -> OTHER
    }
  }

  private companion object {
    val LOWER = IElementType("lower", Language.ANY)
    val UPPER = IElementType("upper", Language.ANY)
    val VOWEL = IElementType("vowel", Language.ANY)
    val OTHER = IElementType("other", Language.ANY)
  }
}
