// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.comparison.iterables.DiffIterableUtil.fair
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.ex.*
import com.intellij.openapi.vcs.ex.LocalLineStatusTracker.Mode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase.assertOrderedEquals
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.containers.ContainerUtil
import java.util.*

abstract class BaseLineStatusTrackerTestCase : BaseLineStatusTrackerManagerTest() {
  protected fun test(text: String, task: Test.() -> Unit) {
    test(text, text, false, task)
  }

  protected fun test(text: String, vcsText: String, smart: Boolean = false, task: Test.() -> Unit) {
    resetTestState()
    VcsApplicationSettings.getInstance().SHOW_WHITESPACES_IN_LST = smart
    arePartialChangelistsSupported = false

    doTest(text, vcsText, { tracker -> Test(tracker as SimpleLocalLineStatusTracker) }, task)
  }

  protected fun testPartial(text: String, task: PartialTest.() -> Unit) {
    testPartial(text, text, task)
  }

  protected fun testPartial(text: String, vcsText: String, task: PartialTest.() -> Unit) {
    resetTestState()

    doTest(text, vcsText, { tracker -> PartialTest(tracker as ChangelistsLocalLineStatusTracker) }, task)
  }

  private fun <TestHelper : Test> doTest(text: String, vcsText: String,
                                         createTestHelper: (LineStatusTracker<*>) -> TestHelper,
                                         task: TestHelper.() -> Unit) {
    val fileName = "file.txt"
    val file = addLocalFile(fileName, parseInput(text))
    setBaseVersion(fileName, parseInput(vcsText))
    refreshCLM()

    file.withOpenedEditor {
      lstm.waitUntilBaseContentsLoaded()

      val testHelper = createTestHelper(file.tracker!!)
      testHelper.verify()
      task(testHelper)
      testHelper.verify()
    }
  }

  protected fun lightTest(text: String, vcsText: String, smart: Boolean = false, task: Test.() -> Unit) {
    val file = LightVirtualFile("LSTTestFile", PlainTextFileType.INSTANCE, parseInput(text))
    val document = FileDocumentManager.getInstance().getDocument(file)!!
    val tracker = runWriteAction {
      val tracker = SimpleLocalLineStatusTracker.createTracker(getProject(), document, file, Mode(true, true, smart))
      tracker.setBaseRevision(parseInput(vcsText))
      tracker
    }

    try {
      val testHelper = Test(tracker)
      testHelper.verify()
      task(testHelper)
      testHelper.verify()
    }
    finally {
      tracker.release()
    }
  }


  protected open inner class Test(val tracker: LocalLineStatusTracker<*>) {
    val file: VirtualFile = tracker.virtualFile
    val document: Document = tracker.document
    val vcsDocument: Document = tracker.vcsDocument
    private val documentTracker = tracker.getDocumentTrackerInTestMode()

    fun assertHelperContentIs(expected: String, helper: PartialCommitHelper) {
      assertEquals(parseInput(expected), helper.content)
    }

    fun assertTextContentIs(expected: String) {
      tracker.assertTextContentIs(expected)
    }

    fun assertBaseTextContentIs(expected: String) {
      tracker.assertBaseTextContentIs(expected)
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


    fun runCommandVerify(task: () -> Unit) {
      this@BaseLineStatusTrackerTestCase.runCommand(null, task)
      verify()
    }

    fun insertAtStart(text: String) {
      runCommandVerify { document.insertString(0, parseInput(text)) }
    }

    fun TestRange.insertBefore(text: String) {
      runCommandVerify { document.insertString(this.start, parseInput(text)) }
    }

    fun TestRange.insertAfter(text: String) {
      runCommandVerify { document.insertString(this.end, parseInput(text)) }
    }

    fun TestRange.delete() {
      runCommandVerify { document.deleteString(this.start, this.end) }
    }

    fun TestRange.replace(text: String) {
      runCommandVerify { document.replaceString(this.start, this.end, parseInput(text)) }
    }

    fun replaceWholeText(text: String) {
      runCommandVerify { document.replaceString(0, document.textLength, parseInput(text)) }
    }

    fun stripTrailingSpaces() {
      (document as DocumentImpl).stripTrailingSpaces(null, true)
      verify()
    }

    fun rollbackLine(line: Int) {
      rollbackLines(BitSet().apply { set(line) })
    }

    fun rollbackLines(lines: BitSet) {
      runCommandVerify { tracker.rollbackChanges(lines) }
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
    inner class Helper(val start: Int)

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
      runCommandVerify {
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

        assertFalse(f1 == f2)
        assertFalse(l1 == l2)
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

  protected inner class PartialTest(val partialTracker: ChangelistsLocalLineStatusTracker) : Test(partialTracker) {
    fun assertAffectedChangeLists(vararg expected: String) {
      partialTracker.assertAffectedChangeLists(*expected)
    }

    fun createChangeList_SetDefault(list: String) {
      clm.addChangeList(list, null)
      clm.setDefaultChangeList(list)
    }


    fun handlePartialCommit(side: Side, list: String, honorExcludedFromCommit: Boolean = true): PartialCommitHelper {
      return partialTracker.handlePartialCommit(side, listOf(list.asListNameToId()), honorExcludedFromCommit)
    }


    fun Range.moveTo(list: String) {
      val changeList = clm.addChangeList(list, null)
      partialTracker.moveToChangelist(this, changeList)
    }

    fun moveChangesTo(lines: BitSet, list: String) {
      val changeList = clm.addChangeList(list, null)
      partialTracker.moveToChangelist(lines, changeList)
    }


    fun undo() {
      undo(document)
    }

    fun redo() {
      redo(document)
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
