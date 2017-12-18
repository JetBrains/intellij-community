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
package com.intellij.openapi.vcs

import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.comparison.iterables.DiffIterableUtil.fair
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.ex.LineStatusTracker
import com.intellij.openapi.vcs.ex.LineStatusTracker.Mode
import com.intellij.openapi.vcs.ex.Range
import com.intellij.openapi.vcs.ex.createRanges
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightPlatformTestCase.assertOrderedEquals
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.containers.ContainerUtil
import java.util.*

abstract class BaseLineStatusTrackerTestCase : LightPlatformTestCase() {
  protected lateinit var myTracker: LineStatusTracker<*>

  protected val myDocument: Document get() = myTracker.document
  protected val myUpToDateDocument: Document get() = myTracker.vcsDocument

  public override fun tearDown() {
    try {
      releaseTracker()
    }
    finally {
      super.tearDown()
    }
  }

  protected fun runCommand(task: () -> Unit) {
    CommandProcessor.getInstance().executeCommand(getProject(),
                                                  { ApplicationManager.getApplication().runWriteAction(task) }, "", null)
    verify()
  }

  protected fun replaceString(startOffset: Int, endOffset: Int, s: String) {
    runCommand { myDocument.replaceString(startOffset, endOffset, s) }
  }

  protected fun insertString(offset: Int, s: String) {
    runCommand { myDocument.insertString(offset, s) }
  }

  protected fun deleteString(startOffset: Int, endOffset: Int) {
    runCommand { myDocument.deleteString(startOffset, endOffset) }
  }

  protected fun rollback(range: Range) {
    runCommand { myTracker.rollbackChanges(range) }
  }

  protected fun rollback(lines: BitSet) {
    runCommand { myTracker.rollbackChanges(lines) }
  }

  protected fun compareRanges() {
    val expected = createRanges(myDocument, myUpToDateDocument)
    val actual = myTracker.getRanges()
    assertEqualRanges(expected, actual)
  }

  fun stripTrailingSpaces() {
    (myDocument as DocumentImpl).stripTrailingSpaces(null, true)
  }

  fun assertTextContentIs(expected: String) {
    assertEquals(expected, myDocument.text)
  }

  protected fun createDocument(text: String) {
    createDocument(text, text)
    compareRanges()
    assertEquals(0, DocumentMarkupModel.forDocument(myDocument, getProject(), true).allHighlighters.size)
  }

  protected fun createDocument(text: String, upToDateDocument: String, smart: Boolean = false) {
    val file = LightVirtualFile("LSTTestFile", PlainTextFileType.INSTANCE, text)
    val document = FileDocumentManager.getInstance().getDocument(file)!!
    ApplicationManager.getApplication().runWriteAction {
      myTracker = LineStatusTracker.createOn(file, document, getProject(), if (smart) Mode.SMART else Mode.DEFAULT)
      myTracker.setBaseRevision(upToDateDocument)
    }
  }

  protected fun releaseTracker() {
    myTracker.release()
  }


  fun verify() {
    checkValid()
    checkCantTrim()
    checkCantMerge()
    checkInnerRanges()
  }

  private fun checkValid() {
    val ranges = myTracker.getRanges()!!
    val diffRanges = ContainerUtil.map(ranges) { it -> com.intellij.diff.util.Range(it.vcsLine1, it.vcsLine2, it.line1, it.line2) }

    val lineCount1 = DiffUtil.getLineCount(myUpToDateDocument)
    val lineCount2 = DiffUtil.getLineCount(myDocument)
    val iterable = fair(DiffIterableUtil.create(diffRanges, lineCount1, lineCount2))

    for (range in iterable.iterateUnchanged()) {
      val lines1 = DiffUtil.getLines(myUpToDateDocument, range.start1, range.end1)
      val lines2 = DiffUtil.getLines(myDocument, range.start2, range.end2)
      assertOrderedEquals(lines1, lines2)
    }
  }

  private fun checkCantTrim() {
    val ranges = myTracker.getRanges()!!
    for (range in ranges) {
      if (range.type != Range.MODIFIED) continue

      val lines1 = DiffUtil.getLines(myUpToDateDocument, range.vcsLine1, range.vcsLine2)
      val lines2 = DiffUtil.getLines(myDocument, range.line1, range.line2)

      val f1 = ContainerUtil.getFirstItem(lines1)
      val f2 = ContainerUtil.getFirstItem(lines2)

      val l1 = ContainerUtil.getLastItem(lines1)
      val l2 = ContainerUtil.getLastItem(lines2)

      assertFalse(Comparing.equal(f1, f2))
      assertFalse(Comparing.equal(l1, l2))
    }
  }

  private fun checkCantMerge() {
    val ranges = myTracker.getRanges()!!
    for (i in 0 until ranges.size - 1) {
      assertFalse(ranges[i].line2 == ranges[i + 1].line1)
    }
  }

  private fun checkInnerRanges() {
    val ranges = myTracker.getRanges()!!

    for (range in ranges) {
      val innerRanges = range.innerRanges ?: continue
      if (range.type != Range.MODIFIED) {
        assertEmpty(innerRanges)
        continue
      }

      var last = 0
      for (innerRange in innerRanges) {
        assertEquals(innerRange.line1 == innerRange.line2, innerRange.type == Range.DELETED)

        assertEquals(last, innerRange.line1)
        last = innerRange.line2
      }
      assertEquals(last, range.line2 - range.line1)

      val lines1 = DiffUtil.getLines(myUpToDateDocument, range.vcsLine1, range.vcsLine2)
      val lines2 = DiffUtil.getLines(myDocument, range.line1, range.line2)

      var start = 0
      for (innerRange in innerRanges) {
        if (innerRange.type != Range.EQUAL) continue

        for (i in innerRange.line1 until innerRange.line2) {
          val line = lines2[i]
          val searchSpace = lines1.subList(start, lines1.size)
          val index = ContainerUtil.indexOf(searchSpace) { it -> StringUtil.equalsIgnoreWhitespaces(it, line) }
          assertTrue(index != -1)
          start += index + 1
        }
      }
    }
  }

  companion object {
    @JvmStatic
    fun assertEqualRanges(expected: List<Range>, actual: List<Range>?) {
      assertOrderedEquals("", actual!!, expected) { r1, r2 ->
        r1.line1 == r2.line1 &&
        r1.line2 == r2.line2 &&
        r1.vcsLine1 == r2.vcsLine1 &&
        r1.vcsLine2 == r2.vcsLine2
      }
    }
  }
}
