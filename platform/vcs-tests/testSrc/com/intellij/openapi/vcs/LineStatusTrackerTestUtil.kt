// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.DiffRangeUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.LineStatusTrackerTestUtil.assertBaseTextContentIs
import com.intellij.openapi.vcs.LineStatusTrackerTestUtil.assertEqualRanges
import com.intellij.openapi.vcs.LineStatusTrackerTestUtil.assertTextContentIs
import com.intellij.openapi.vcs.LineStatusTrackerTestUtil.parseInput
import com.intellij.openapi.vcs.ex.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.containers.ContainerUtil
import junit.framework.TestCase
import java.util.*

object LineStatusTrackerTestUtil {
  @JvmStatic
  fun parseInput(input: String) = input.replace('_', '\n')

  @JvmStatic
  fun assertEqualRanges(actual: List<Range>, expected: List<Range>) {
    UsefulTestCase.assertOrderedEquals("", actual, expected) { r1, r2 ->
      r1.line1 == r2.line1 &&
      r1.line2 == r2.line2 &&
      r1.vcsLine1 == r2.vcsLine1 &&
      r1.vcsLine2 == r2.vcsLine2
    }
  }

  fun LineStatusTracker<*>.assertTextContentIs(expected: String) {
    TestCase.assertEquals(parseInput(expected), document.text)
  }

  fun LineStatusTracker<*>.assertBaseTextContentIs(expected: String) {
    TestCase.assertEquals(parseInput(expected), vcsDocument.text)
  }
}

open class TrackerModificationsTest(val tracker: LocalLineStatusTracker<*>) {
  val file: VirtualFile = tracker.virtualFile
  val document: Document = tracker.document
  val vcsDocument: Document = tracker.vcsDocument

  fun assertHelperContentIs(expected: String, helper: PartialCommitHelper) {
    TestCase.assertEquals(parseInput(expected), helper.content)
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
    runCommand(task)
    verify()
  }

  fun runCommand(task: () -> Unit) {
    CommandProcessor.getInstance().executeCommand(tracker.project, {
      ApplicationManager.getApplication().runWriteAction(task)
    }, "", null)
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
    TestCase.assertEquals(parseInput(this), range.text)
    return range
  }

  infix fun String.before(pattern: String): TestRange {
    val patternRange = findPattern(pattern)
    val text = parseInput(this)
    val range = TestRange(patternRange.start - text.length, patternRange.start)
    TestCase.assertEquals(text, range.text)
    return range
  }

  infix fun String.after(pattern: String): TestRange {
    val patternRange = findPattern(pattern)
    val text = parseInput(this)
    val range = TestRange(patternRange.end, patternRange.end + text.length)
    TestCase.assertEquals(text, range.text)
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
    TestCase.assertTrue(firstOffset == lastOffset && firstOffset != -1)
    return TestRange(firstOffset, firstOffset + text.length)
  }

  private fun findPattern(pattern: String, index: Int): TestRange {
    val text = parseInput(pattern)
    TestCase.assertTrue(index >= 0)

    var offset = -1
    for (i in 0..index) {
      val newOffset = document.immutableCharSequence.indexOf(text, offset + 1)
      TestCase.assertTrue(newOffset >= 0 && (offset == -1 || newOffset >= offset + text.length))
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
    TestCase.assertEquals(parseInput(text), actual)
  }

  fun Range.assertType(type: Byte) {
    TestCase.assertEquals(type, this.type)
  }

  fun Range.rollback() {
    runCommandVerify {
      tracker.rollbackChanges(this)
    }
  }


  open fun verify() {
    if (tracker.isValid()) {
      checkRangesAreValid()
      checkCantTrim()
      checkCantMerge()
      checkInnerRanges()
    }
  }

  protected fun checkRangesAreValid() {
    val diffRanges = tracker.getRanges()!!.map { com.intellij.diff.util.Range(it.vcsLine1, it.vcsLine2, it.line1, it.line2) }
    checkRangesAreValid(vcsDocument, document, diffRanges)
  }

  protected fun checkRangesAreValid(document1: Document, document2: Document, diffRanges: List<com.intellij.diff.util.Range>) {
    val content1 = document1.charsSequence
    val content2 = document2.charsSequence
    val lineOffsets1 = LineOffsetsUtil.create(document1)
    val lineOffsets2 = LineOffsetsUtil.create(document2)

    val iterable = DiffIterableUtil.fair(DiffIterableUtil.create(diffRanges, lineOffsets1.lineCount, lineOffsets2.lineCount))

    for (range in iterable.iterateUnchanged()) {
      val lines1 = DiffRangeUtil.getLines(content1, lineOffsets1, range.start1, range.end1)
      val lines2 = DiffRangeUtil.getLines(content2, lineOffsets2, range.start2, range.end2)
      UsefulTestCase.assertOrderedEquals(lines1, lines2)
    }
  }

  protected fun checkCantTrim() {
    val ranges = tracker.getRanges()!!
    for (range in ranges) {
      if (range.type != Range.MODIFIED) continue

      val lines1 = getVcsLines(range)
      val lines2 = getCurrentLines(range)

      val f1 = ContainerUtil.getFirstItem(lines1)
      val f2 = ContainerUtil.getFirstItem(lines2)

      val l1 = ContainerUtil.getLastItem(lines1)
      val l2 = ContainerUtil.getLastItem(lines2)

      TestCase.assertFalse(f1 == f2)
      TestCase.assertFalse(l1 == l2)
    }
  }

  protected fun checkCantMerge() {
    val ranges = tracker.getRanges()!!
    for (i in 0 until ranges.size - 1) {
      TestCase.assertFalse(ranges[i].line2 == ranges[i + 1].line1)
    }
  }

  protected fun checkInnerRanges() {
    val ranges = tracker.getRanges()!!

    for (range in ranges) {
      val innerRanges = range.innerRanges ?: continue
      if (range.type != Range.MODIFIED) {
        UsefulTestCase.assertEmpty(innerRanges)
        continue
      }

      var last = 0
      for (innerRange in innerRanges) {
        TestCase.assertEquals(innerRange.line1 == innerRange.line2, innerRange.type == Range.DELETED)

        TestCase.assertEquals(last, innerRange.line1)
        last = innerRange.line2
      }
      TestCase.assertEquals(last, range.line2 - range.line1)

      val lines1 = getVcsLines(range)
      val lines2 = getCurrentLines(range)

      var start = 0
      for (innerRange in innerRanges) {
        if (innerRange.type != Range.EQUAL) continue

        for (i in innerRange.line1 until innerRange.line2) {
          val line = lines2[i]
          val searchSpace = lines1.subList(start, lines1.size)
          val index = ContainerUtil.indexOf(searchSpace) { it -> StringUtil.equalsIgnoreWhitespaces(it, line) }
          TestCase.assertTrue(index != -1)
          start += index + 1
        }
      }
    }
  }


  private fun getVcsLines(range: Range): List<String> = DiffUtil.getLines(vcsDocument, range.vcsLine1, range.vcsLine2)
  private fun getCurrentLines(range: Range): List<String> = DiffUtil.getLines(document, range.line1, range.line2)
}
