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
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.changes.LocalChangeListImpl
import com.intellij.openapi.vcs.ex.*
import com.intellij.openapi.vcs.ex.LineStatusTracker.Mode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightPlatformTestCase.assertOrderedEquals
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.containers.ContainerUtil
import java.util.*

private typealias DiffRange = com.intellij.diff.util.Range

abstract class BaseLineStatusTrackerTestCase : LightPlatformTestCase() {
  protected fun test(text: String, task: Test.() -> Unit) {
    test(text, text, false, task)
  }

  protected fun test(text: String, vcsText: String, smart: Boolean = false, task: Test.() -> Unit) {
    val mode = if (smart) Mode.SMART else Mode.DEFAULT
    doTest(text, vcsText,
           { document, file -> SimpleLocalLineStatusTracker.createTracker(getProject(), document, file, mode) },
           { tracker -> Test(tracker) },
           task)
  }

  protected fun testPartial(text: String, task: PartialTest.() -> Unit) {
    testPartial(text, text, task)
  }

  protected fun testPartial(text: String, vcsText: String, task: PartialTest.() -> Unit) {
    doTest(text, vcsText,
           { document, file -> PartialLocalLineStatusTracker.createTracker(getProject(), document, file, Mode.SMART) },
           { tracker -> PartialTest(tracker) },
           task)
  }

  protected fun <Tracker : LineStatusTracker<*>, TestHelper : Test> doTest(text: String, vcsText: String,
                                                                           createTracker: (Document, VirtualFile) -> Tracker,
                                                                           createTestHelper: (Tracker) -> TestHelper,
                                                                           task: TestHelper.() -> Unit) {
    val file = LightVirtualFile("LSTTestFile", PlainTextFileType.INSTANCE, parseInput(text))
    val document = FileDocumentManager.getInstance().getDocument(file)!!
    val tracker = runWriteAction {
      val tracker = createTracker(document, file)
      tracker.setBaseRevision(parseInput(vcsText))
      tracker
    }

    try {
      val testHelper = createTestHelper(tracker)
      testHelper.verify()

      task(testHelper)

      testHelper.verify()
    }
    finally {
      tracker.release()
    }
  }


  protected open class Test(val tracker: LineStatusTracker<*>) {
    val file: VirtualFile = tracker.virtualFile
    val document: Document = tracker.document
    val vcsDocument: Document = tracker.vcsDocument
    private val documentTracker = tracker.getDocumentTrackerInTestMode()


    fun assertHelperContentIs(expected: String, helper: PartialLocalLineStatusTracker.PartialCommitHelper) {
      assertEquals(parseInput(expected), helper.content)
    }

    fun assertTextContentIs(expected: String) {
      assertEquals(parseInput(expected), document.text)
    }

    fun assertBaseTextContentIs(expected: String) {
      assertEquals(parseInput(expected), vcsDocument.text)
    }

    fun assertRangesEmpty() {
      assertRanges()
    }

    fun assertRanges(vararg expected: Range) {
      assertEqualRanges(tracker.getRanges()!!, expected.toList())
    }

    fun compareRanges() {
      val expected = createRanges(document, vcsDocument)
      val actual = tracker.getRanges()!!
      assertEqualRanges(actual, expected)
    }


    fun runCommand(task: () -> Unit) {
      CommandProcessor.getInstance().executeCommand(getProject(), {
        ApplicationManager.getApplication().runWriteAction(task)
      }, "", null)

      verify()
    }

    fun insertAtStart(text: String) {
      runCommand { document.insertString(0, parseInput(text)) }
    }

    fun TestRange.insertBefore(text: String) {
      runCommand { document.insertString(this.start, parseInput(text)) }
    }

    fun TestRange.insertAfter(text: String) {
      runCommand { document.insertString(this.end, parseInput(text)) }
    }

    fun TestRange.delete() {
      runCommand { document.deleteString(this.start, this.end) }
    }

    fun TestRange.replace(text: String) {
      runCommand { document.replaceString(this.start, this.end, parseInput(text)) }
    }

    fun replaceWholeText(text: String) {
      runCommand { document.replaceString(0, document.textLength, parseInput(text)) }
    }

    fun stripTrailingSpaces() {
      (document as DocumentImpl).stripTrailingSpaces(null, true)
      verify()
    }

    fun rollbackLine(line: Int) {
      rollbackLines(BitSet().apply { set(line) })
    }

    fun rollbackLines(lines: BitSet) {
      runCommand { tracker.rollbackChanges(lines) }
    }


    fun String.insertBefore(text: String) {
      findPattern(this).insertBefore(text)
    }

    fun String.insertAfter(text: String) {
      findPattern(this).insertAfter(text)
    }

    fun String.delete() {
      findPattern(this).delete()
    }

    fun String.replace(text: String) {
      findPattern(this).replace(text)
    }


    inner class TestRange(val start: Int, val end: Int) {
      val text get() = document.charsSequence.subSequence(this.start, this.end).toString()
    }

    operator fun Int.not(): Helper = Helper(this)
    operator fun Helper.minus(end: Int): TestRange = TestRange(this.start, end)
    class Helper(val start: Int)

    infix fun String.at(range: TestRange): TestRange {
      assertEquals(parseInput(this), range.text)
      return range
    }

    infix fun String.before(pattern: String): TestRange {
      val patternRange = findPattern(pattern)
      val text = parseInput(this)
      val range = TestRange(patternRange.start - text.length, patternRange.start)
      assertEquals(text, range.text)
      return range
    }

    infix fun String.after(pattern: String): TestRange {
      val patternRange = findPattern(pattern)
      val text = parseInput(this)
      val range = TestRange(patternRange.end, patternRange.end + text.length)
      assertEquals(text, range.text)
      return range
    }

    infix fun String.`in`(pattern: String): TestRange {
      val patternRange = findPattern(pattern)
      val patternText = patternRange.text
      val range = findPattern(this, patternText)
      return TestRange(patternRange.start + range.start, patternRange.start + range.end)
    }

    infix fun Int.th(text: String): TestRange = findPattern(text, this - 1)


    private fun findPattern(pattern: String): TestRange = findPattern(pattern, document.immutableCharSequence)

    private fun findPattern(pattern: String, sequence: CharSequence): TestRange {
      val text = parseInput(pattern)
      val firstOffset = sequence.indexOf(text)
      val lastOffset = sequence.lastIndexOf(text)
      assertTrue(firstOffset == lastOffset && firstOffset != -1)
      return TestRange(firstOffset, firstOffset + text.length)
    }

    private fun findPattern(pattern: String, index: Int): TestRange {
      val text = parseInput(pattern)
      assertTrue(index >= 0)

      var offset = -1
      for (i in 0..index) {
        val newOffset = document.immutableCharSequence.indexOf(text, offset + 1)
        assertTrue(newOffset >= 0 && (offset == -1 || newOffset >= offset + text.length))
        offset = newOffset
      }
      return TestRange(offset, offset + pattern.length)
    }


    fun range(): Range {
      return tracker.getRanges()!!.single()
    }

    fun range(index: Int): Range {
      return tracker.getRanges()!![index]
    }

    fun Range.assertVcsContent(text: String) {
      val actual = DiffUtil.getLinesContent(tracker.vcsDocument, this.vcsLine1, this.vcsLine2).toString()
      assertEquals(parseInput(text), actual)
    }

    fun Range.assertType(type: Byte) {
      assertEquals(type,  this.type)
    }

    fun Range.rollback() {
      runCommand {
        tracker.rollbackChanges(this)
      }
    }


    fun verify() {
      checkBlocksAreValid()

      if (!documentTracker.isFrozen()) {
        checkCantTrim()
        checkCantMerge()
        checkInnerRanges()
      }
    }

    private fun checkBlocksAreValid() {
      val content1 = documentTracker.getContent(Side.LEFT)
      val content2 = documentTracker.getContent(Side.RIGHT)
      val lineOffsets1 = LineOffsetsUtil.create(content1)
      val lineOffsets2 = LineOffsetsUtil.create(content2)

      val diffRanges = documentTracker.blocks.map { it.range }.filter { !it.isEmpty }
      val iterable = fair(DiffIterableUtil.create(diffRanges, lineOffsets1.lineCount, lineOffsets2.lineCount))

      for (range in iterable.iterateUnchanged()) {
        val lines1 = DiffUtil.getLines(content1, lineOffsets1, range.start1, range.end1)
        val lines2 = DiffUtil.getLines(content2, lineOffsets2, range.start2, range.end2)
        assertOrderedEquals(lines1, lines2)
      }
    }

    private fun checkCantTrim() {
      val ranges = tracker.getRanges()!!
      for (range in ranges) {
        if (range.type != Range.MODIFIED) continue

        val lines1 = getVcsLines(range)
        val lines2 = getCurrentLines(range)

        val f1 = ContainerUtil.getFirstItem(lines1)
        val f2 = ContainerUtil.getFirstItem(lines2)

        val l1 = ContainerUtil.getLastItem(lines1)
        val l2 = ContainerUtil.getLastItem(lines2)

        assertFalse(Comparing.equal(f1, f2))
        assertFalse(Comparing.equal(l1, l2))
      }
    }

    private fun checkCantMerge() {
      val ranges = tracker.getRanges()!!
      for (i in 0 until ranges.size - 1) {
        assertFalse(ranges[i].line2 == ranges[i + 1].line1)
      }
    }

    private fun checkInnerRanges() {
      val ranges = tracker.getRanges()!!

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

        val lines1 = getVcsLines(range)
        val lines2 = getCurrentLines(range)

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


    private fun getVcsLines(range: Range): List<String> = DiffUtil.getLines(vcsDocument, range.vcsLine1, range.vcsLine2)
    private fun getCurrentLines(range: Range): List<String> = DiffUtil.getLines(document, range.line1, range.line2)
  }

  protected class PartialTest(val partialTracker: PartialLocalLineStatusTracker) : Test(partialTracker) {
    var defaultList: String = "Default"
    val changelists = mutableSetOf(defaultList)

    init {
      partialTracker.initChangeTracking(defaultList, changelists.toList())
    }


    fun assertAffectedChangelists(vararg expected: String) {
      assertSameElements(partialTracker.affectedChangeListsIds, expected.toList())
    }

    fun Range.assertChangelist(list: String) {
      val localRange = this as PartialLocalLineStatusTracker.LocalRange
      assertEquals(localRange.changelistId, list)
    }


    fun createChangelist(list: String) {
      assertDoesntContain(changelists, list)
      changelists.add(list)
    }

    fun removeChangelist(list: String) {
      assertFalse(defaultList == list)
      assertContainsElements(changelists, list)
      partialTracker.changeListRemoved(list)
      changelists.remove(list)
    }

    fun setDefaultChangelist(list: String) {
      changelists.add(list)
      partialTracker.defaultListChanged(defaultList, list)
      defaultList = list
    }


    fun moveChanges(fromList: String, toList: String) {
      assertContainsElements(changelists, fromList)
      assertContainsElements(changelists, toList)
      partialTracker.moveChanges(fromList, toList)
    }

    fun moveAllChangesTo(toList: String) {
      assertContainsElements(changelists, toList)
      partialTracker.moveChangesTo(toList)
    }


    fun Range.moveTo(list: String) {
      val fakeChangelist = LocalChangeListImpl.createEmptyChangeListImpl(getProject(), list, list)
      partialTracker.moveToChangelist(this, fakeChangelist)
    }

    fun moveChangesTo(lines: BitSet, list: String) {
      val fakeChangelist = LocalChangeListImpl.createEmptyChangeListImpl(getProject(), list, list)
      partialTracker.moveToChangelist(lines, fakeChangelist)
    }
  }

  companion object {
    internal fun parseInput(input: String) = input.replace('_', '\n')

    @JvmStatic
    fun assertEqualRanges(actual: List<Range>, expected: List<Range>) {
      assertOrderedEquals("", actual, expected) { r1, r2 ->
        r1.line1 == r2.line1 &&
        r1.line2 == r2.line2 &&
        r1.vcsLine1 == r2.vcsLine1 &&
        r1.vcsLine2 == r2.vcsLine2
      }
    }
  }
}
