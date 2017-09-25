/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework.utils.inlays

import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager
import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.util.TextRange
import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import junit.framework.ComparisonFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import java.util.regex.Pattern


class InlayHintsChecker(private val myFixture: CodeInsightTestFixture) {

  private var isParamHintsEnabledBefore = false

  companion object {
    val pattern: Pattern = Pattern.compile("(<caret>)|(<selection>)|(</selection>)|<(hint|HINT)\\s+text=\"([^\"\n\r]+)\"\\s*/>")

    private val default = ParameterNameHintsSettings()
  }

  fun setUp() {
    val settings = EditorSettingsExternalizable.getInstance()
    isParamHintsEnabledBefore = settings.isShowParameterNameHints
    settings.isShowParameterNameHints = true
  }

  fun tearDown() {
    EditorSettingsExternalizable.getInstance().isShowParameterNameHints = isParamHintsEnabledBefore
    val hintSettings = ParameterNameHintsSettings.getInstance()

    hintSettings.loadState(default.state)
  }

  fun checkInlays() {
    val file = myFixture.file
    val document = myFixture.getDocument(file)
    val originalText = document.text
    val expectedInlaysAndCaret = extractInlaysAndCaretInfo(document)
    myFixture.doHighlighting()
    verifyInlaysAndCaretInfo(expectedInlaysAndCaret, originalText)
  }

  fun verifyInlaysAndCaretInfo(expectedInlaysAndCaret: CaretAndInlaysInfo, originalText: String) {
    val file = myFixture.file
    val document = myFixture.getDocument(file)
    val actual: List<Triple<Int, String, Boolean>> = getActualInlays()

    val expected = expectedInlaysAndCaret.inlays

    if (expectedInlaysAndCaret.inlays.size != actual.size || actual.zip(expected).any { it.first != it.second }) {
      val entries: MutableList<Pair<Int, String>> = mutableListOf()
      actual.forEach { entries.add(Pair(it.first, "<${if(it.third) "HINT" else "hint"} text=\"${it.second}\" />")) }
      if (expectedInlaysAndCaret.caretOffset != null) {
        val actualCaretOffset = myFixture.editor.caretModel.offset
        val actualInlaysBeforeCaret = myFixture.editor.caretModel.visualPosition.column - 
                                      myFixture.editor.offsetToVisualPosition(actualCaretOffset).column
        val first = entries.indexOfFirst { it.first == actualCaretOffset }
        val insertIndex = if (first == -1) -entries.binarySearch { it.first - actualCaretOffset } - 1 
                              else first + actualInlaysBeforeCaret;
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

  private fun getActualInlays(): List<Triple<Int, String, Boolean>> {
    val editor = myFixture.editor
    val allInlays = editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength)

    val hintManager = ParameterHintsPresentationManager.getInstance()
    return allInlays
      .filter { hintManager.isParameterHint(it) }
      .map { Triple(it.offset, hintManager.getHintText(it), hintManager.isHighlighted(it))}
      .sortedBy { it.first }
  }

  fun extractInlaysAndCaretInfo(document: Document): CaretAndInlaysInfo {
    val text = document.text
    val matcher = pattern.matcher(text)

    val inlays = mutableListOf<Triple<Int, String, Boolean>>()
    var extractedLength = 0
    var caretOffset : Int? = null
    var inlaysBeforeCaret = 0
    var selectionStart : Int? = null
    var selectionEnd : Int? = null

    while (matcher.find()) {
      val start = matcher.start()
      val matchedLength = matcher.end() - start

      val realStartOffset = start - extractedLength
      if (matcher.group(1) != null) {
        caretOffset = realStartOffset
        inlays.asReversed()
          .takeWhile { it.first == caretOffset }
          .forEach { inlaysBeforeCaret++ }
      }
      else if (matcher.group(2) != null) {
        selectionStart = realStartOffset
      }
      else if (matcher.group(3) != null) {
        selectionEnd = realStartOffset
      }
      else {
        inlays += Triple(realStartOffset, matcher.group(5), matcher.group(4) == "HINT")
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
                          val inlays: List<Triple<Int, String, Boolean>>)