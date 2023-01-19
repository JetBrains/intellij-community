// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.inlays

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager
import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.util.TextRange
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import junit.framework.ComparisonFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import java.util.regex.Pattern


class InlayHintsChecker(private val myFixture: CodeInsightTestFixture) {

  companion object {
    val pattern: Pattern = Pattern.compile("(<caret>)|(<selection>)|(</selection>)|<(hint|HINT|Hint|hINT)\\s+text=\"([^\n\r]+?(?=\"\\s*/>))\"\\s*/>")

    private val default = ParameterNameHintsSettings()
  }

  fun setUp() {
  }

  fun tearDown() {
    val hintSettings = ParameterNameHintsSettings.getInstance()

    hintSettings.loadState(default.state)
  }

  val manager = ParameterHintsPresentationManager.getInstance()
  private val inlayPresenter: (Inlay<*>) -> String = { (it.renderer as HintRenderer).text ?: throw IllegalArgumentException("No text set to hint") }
  private val inlayFilter: (Inlay<*>) -> Boolean = { manager.isParameterHint(it) }

  fun checkParameterHints() = checkInlays(inlayPresenter, inlayFilter)

  fun checkInlays(inlayPresenter: (Inlay<*>) -> String, inlayFilter: (Inlay<*>) -> Boolean) {
    val file = myFixture.file!!
    val document = myFixture.getDocument(file)
    val originalText = document.text
    val expectedInlaysAndCaret = extractInlaysAndCaretInfo(document)
    myFixture.doHighlighting()
    verifyInlaysAndCaretInfo(expectedInlaysAndCaret, originalText, inlayPresenter, inlayFilter)
  }

  fun verifyInlaysAndCaretInfo(expectedInlaysAndCaret: CaretAndInlaysInfo,
                               originalText: String) =
    verifyInlaysAndCaretInfo(expectedInlaysAndCaret, originalText, inlayPresenter, inlayFilter)

  private fun verifyInlaysAndCaretInfo(expectedInlaysAndCaret: CaretAndInlaysInfo,
                                       originalText: String,
                                       inlayPresenter: (Inlay<*>) -> String,
                                       inlayFilter: (Inlay<*>) -> Boolean) {
    val file = myFixture.file!!
    val document = myFixture.getDocument(file)
    val actual: List<InlayInfo> = getActualInlays(inlayPresenter, inlayFilter)

    val expected = expectedInlaysAndCaret.inlays

    if (expectedInlaysAndCaret.inlays.size != actual.size || actual.zip(expected).any { it.first != it.second }) {
      val entries: MutableList<Pair<Int, String>> = mutableListOf()
      actual.forEach { entries.add(Pair(it.offset, buildString {
        append("<")
        append((if (it.highlighted) "H" else "h"))
        append((if (it.current) "INT" else "int"))
        append(" text=\"")
        append(it.text)
        append("\"/>")
      }))}
      if (expectedInlaysAndCaret.caretOffset != null) {
        val actualCaretOffset = myFixture.editor.caretModel.offset
        val actualInlaysBeforeCaret = myFixture.editor.caretModel.visualPosition.column -
                                      myFixture.editor.offsetToVisualPosition(actualCaretOffset).column
        val first = entries.indexOfFirst { it.first == actualCaretOffset }
        val insertIndex = if (first == -1) -entries.binarySearch { it.first - actualCaretOffset } - 1
                              else first + actualInlaysBeforeCaret
        entries.add(insertIndex, Pair(actualCaretOffset, "<caret>"))
      }
      val proposedText = StringBuilder(document.text)
      entries.asReversed().forEach { proposedText.insert(it.first, it.second) }

      VfsTestUtil.TEST_DATA_FILE_PATH.get(file.virtualFile)?.let { originalPath ->
        throw FileComparisonFailure("Hints differ", originalText, proposedText.toString(), originalPath)
      } ?: throw ComparisonFailure("Hints differ", originalText, proposedText.toString())
    }

    if (expectedInlaysAndCaret.caretOffset != null) {
      assertEquals("Unexpected caret offset", expectedInlaysAndCaret.caretOffset, myFixture.editor.caretModel.offset)
      val position = myFixture.editor.offsetToVisualPosition(expectedInlaysAndCaret.caretOffset)
      assertEquals("Unexpected caret visual position",
                   VisualPosition(position.line, position.column + expectedInlaysAndCaret.inlaysBeforeCaret),
                   myFixture.editor.caretModel.visualPosition)
      val selectionModel = myFixture.editor.selectionModel
      if (expectedInlaysAndCaret.selection == null) assertFalse(selectionModel.hasSelection())
      else assertEquals("Unexpected selection",
                        expectedInlaysAndCaret.selection,
                        TextRange(selectionModel.selectionStart, selectionModel.selectionEnd))
    }
  }

  private fun getActualInlays(inlayPresenter: (Inlay<*>) -> String,
                              inlayFilter: (Inlay<*>) -> Boolean): List<InlayInfo> {
    val editor = myFixture.editor
    val allInlays = editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength) +
                    editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength)

    val hintManager = ParameterHintsPresentationManager.getInstance()
    return allInlays
      .filterNotNull()
      .filter { inlayFilter(it) }
      .map {
        val isHighlighted: Boolean
        val isCurrent: Boolean
        if (hintManager.isParameterHint(it)) {
          isHighlighted = hintManager.isHighlighted(it)
          isCurrent = hintManager.isCurrent(it)
        } else {
          isHighlighted = false
          isCurrent = false
        }
        InlayInfo(it.offset, inlayPresenter(it),  isHighlighted, isCurrent)
      }
      .sortedBy { it.offset }
  }

  fun extractInlaysAndCaretInfo(document: Document): CaretAndInlaysInfo {
    val text = document.text
    val matcher = pattern.matcher(text)

    val inlays = mutableListOf<InlayInfo>()
    var extractedLength = 0
    var caretOffset : Int? = null
    var inlaysBeforeCaret = 0
    var selectionStart : Int? = null
    var selectionEnd : Int? = null

    while (matcher.find()) {
      val start = matcher.start()
      val matchedLength = matcher.end() - start

      val realStartOffset = start - extractedLength
      when {
        matcher.group(1) != null -> {
          caretOffset = realStartOffset
          inlays.asReversed()
            .takeWhile { it.offset == caretOffset }
            .forEach { inlaysBeforeCaret++ }
        }
        matcher.group(2) != null -> selectionStart = realStartOffset
        matcher.group(3) != null -> selectionEnd = realStartOffset
        else -> inlays += InlayInfo(realStartOffset, matcher.group(5), matcher.group(4).startsWith("H"), matcher.group(4).endsWith("INT"))
      }

      removeText(document, realStartOffset, matchedLength)
      extractedLength += (matcher.end() - start)
    }

    return CaretAndInlaysInfo(caretOffset, inlaysBeforeCaret,
                              if (selectionStart == null || selectionEnd == null) null else TextRange(selectionStart, selectionEnd),
                              inlays)
  }

  private fun removeText(document: Document, realStartOffset: Int, matchedLength: Int) {
    WriteCommandAction.runWriteCommandAction(myFixture.project, {
      document.replaceString(realStartOffset, realStartOffset + matchedLength, "")
    })
  }


}

class CaretAndInlaysInfo (val caretOffset: Int?, val inlaysBeforeCaret: Int, val selection: TextRange?,
                          val inlays: List<InlayInfo>)

data class InlayInfo (val offset: Int, val text: String, val highlighted: Boolean, val current: Boolean)