// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.vcs.LineStatusTrackerTestUtil.assertTextContentIs
import com.intellij.openapi.vcs.LineStatusTrackerTestUtil.parseInput
import com.intellij.openapi.vcs.TrackerModificationsTest
import com.intellij.openapi.vcs.ex.LineStatusTracker
import com.intellij.openapi.vcs.ex.Range
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightVirtualFile
import junit.framework.TestCase

class GitStageLineStatusTrackerTest : LightPlatformTestCase() {
  fun testRangeTypes() {
    test("A_X_C_X_E_F_G") {
      assertInvalid()

      setBaseContents("A_B_C_D_E_F_G", "A_B_C_X_E_X_G")
      assertRangesCount(3)

      range(0).assertUnstaged()
      range(1).assertStaged()
      range(2).assertConflict()

      range(0).assertContent("B", "B", "X")
      range(1).assertContent("D", "X", "X")
      range(2).assertContent("F", "X", "F")
    }
  }

  fun testStageOperations() {
    test("A_B_C") {
      "B".replace("12_13")
      assertInvalid()

      setBaseContents("A_B_C", "A_B_C")
      assertRangesCount(1)

      range(0).assertUnstaged()
      range(0).assertContent("B", "B", "12_13")

      range(0).stage()
      range(0).assertStaged()
      range(0).assertContent("B", "12_13", "12_13")
      tracker.assertStagedTextContentIs("A_12_13_C")

      range(0).unstage()
      range(0).assertUnstaged()
      range(0).assertContent("B", "B", "12_13")
      tracker.assertStagedTextContentIs("A_B_C")
    }

    test("A_B_C") {
      "B_".delete()
      assertInvalid()

      setBaseContents("A_B_C", "A_B_C")
      assertRangesCount(1)

      range(0).assertUnstaged()
      range(0).assertContent("B", "B", null)

      rollbackLine(1)
      assertRangesEmpty()

      "B".delete()
      range(0).assertUnstaged()
      range(0).assertContent("B", "B", "")
    }

    test("A_B_C") {
      setBaseContents("A_B_C", "A_B_C")
      assertRangesEmpty()

      "B_".delete()
      range().stage()

      "A_".insertAfter("X_")
      range().assertConflict()
      range().assertContent("B", null, "X")
      tracker.assertStagedTextContentIs("A_C")

      range().rollback()
      range().assertContent("B", null, null)
      tracker.assertStagedTextContentIs("A_C")
      tracker.assertTextContentIs("A_C")

      range().unstage()
      range().assertContent("B", "B", null)
      tracker.assertStagedTextContentIs("A_B_C")

      range().rollback()
      assertRangesEmpty()
    }
  }

  fun testAddDeleteConflicts() {
    test("A_C") {
      setBaseContents("A_C", "A_C")
      assertRangesEmpty()

      "A_".insertAfter("X_")
      range().stage()

      "X_".delete()
      range().assertConflict()
      range().assertContent(null, "X", null)

      range().rollback()
      range().assertStaged()
      range().assertContent(null, "X", "X")

      "X_".delete()
      range().assertConflict()
      range().assertContent(null, "X", null)

      range().stage()
      assertRangesEmpty()

      "A_".insertAfter("X_")
      range().stage()
      "X_".delete()
      range().assertConflict()
      range().assertContent(null, "X", null)

      range().unstage()
      assertRangesEmpty()
    }

    test("A_B_C") {
      setBaseContents("A_B_C", "A_B_C")
      assertRangesEmpty()

      "B_".delete()
      range().stage()
      "A_".insertAfter("B_")

      range().assertConflict()
      range().assertContent("B", null, "B")

      range().rollback()
      range().assertStaged()
      range().assertContent("B", null, null)

      "A_".insertAfter("B_")
      range().assertConflict()
      range().assertContent("B", null, "B")

      range().stage()
      assertRangesEmpty()

      "B_".delete()
      range().stage()
      "A_".insertAfter("B_")
      range().assertConflict()
      range().assertContent("B", null, "B")

      range().unstage()
      assertRangesEmpty()
    }
  }

  fun testConflictIntersections() {
    test("A_B_C_D_E") {
      setBaseContents("A_B_C_D_E", "A_B_C_D_E")
      assertRangesEmpty()

      "B".replace("X")
      "D".replace("X")
      range(0).stage()

      range(0).assertStaged()
      range(1).assertUnstaged()

      "C".replace("12_13")
      range().assertConflict()
      range().assertContent("B_C_D", "X_C_D", "X_12_13_X")

      range().rollback()
      range().assertStaged()
      range().assertContent("B", "X", "X")
    }
  }


  private fun test(text: String, task: StagedTest.() -> Unit) {
    val file = LightVirtualFile("LSTTestFile", PlainTextFileType.INSTANCE, parseInput(text))
    val document = FileDocumentManager.getInstance().getDocument(file)!!
    val tracker = GitStageLineStatusTracker(project, file, document)

    try {
      val testHelper = StagedTest(tracker)
      testHelper.verify()
      task(testHelper)
      testHelper.verify()
    }
    finally {
      tracker.release()
    }
  }

  private inner class StagedTest(val stageTracker: GitStageLineStatusTracker) : TrackerModificationsTest(stageTracker) {
    private var _stagedDocument: Document? = null
    val stagedDocument get() = _stagedDocument!!

    fun setBaseContents(headText: String, stagedText: String) {
      _stagedDocument = DocumentImpl(parseInput(stagedText))
      stageTracker.setBaseRevision(parseInput(headText), stagedDocument)
      verify()
    }

    fun Range.stage() {
      stageTracker.stageChanges(this)
      verify()
    }

    fun Range.unstage() {
      stageTracker.unstageChanges(this)
      verify()
    }


    fun assertInvalid() {
      TestCase.assertEquals(false, tracker.isValid())
    }

    fun assertRangesCount(n: Int) {
      TestCase.assertEquals(n, tracker.getRanges()!!.size)
    }

    fun Range.assertStaged() {
      this as StagedRange
      TestCase.assertEquals(true, hasStaged)
      TestCase.assertEquals(false, hasUnstaged)
    }

    fun Range.assertUnstaged() {
      this as StagedRange
      TestCase.assertEquals(false, hasStaged)
      TestCase.assertEquals(true, hasUnstaged)
    }

    fun Range.assertConflict() {
      this as StagedRange
      TestCase.assertEquals(true, hasStaged)
      TestCase.assertEquals(true, hasUnstaged)
    }

    fun Range.assertContent(head: String?, staged: String?, local: String?) {
      this as StagedRange
      val headContent = if (vcsLine1 != vcsLine2) DiffUtil.getLinesContent(vcsDocument, vcsLine1, vcsLine2) else null
      val stagedContent = if (stagedLine1 != stagedLine2) DiffUtil.getLinesContent(stagedDocument, stagedLine1, stagedLine2) else null
      val localContent = if (line1 != line2) DiffUtil.getLinesContent(document, line1, line2) else null

      TestCase.assertEquals(if (head != null) parseInput(head) else null, headContent?.toString())
      TestCase.assertEquals(if (staged != null) parseInput(staged) else null, stagedContent?.toString())
      TestCase.assertEquals(if (local != null) parseInput(local) else null, localContent?.toString())
    }


    override fun verify() {
      if (tracker.isValid()) {
        val ranges = stageTracker.getRanges()!!

        checkRangesAreValid()

        val stagedDiffRanges = ranges.map { com.intellij.diff.util.Range(it.vcsLine1, it.vcsLine2, it.stagedLine1, it.stagedLine2) }
        checkRangesAreValid(vcsDocument, stagedDocument, stagedDiffRanges)

        val unstagedDiffRanges = ranges.map { com.intellij.diff.util.Range(it.stagedLine1, it.stagedLine2, it.line1, it.line2) }
        checkRangesAreValid(stagedDocument, document, unstagedDiffRanges)
      }
    }
  }

  private fun LineStatusTracker<*>.assertStagedTextContentIs(expected: String) {
    this as GitStageLineStatusTracker
    TestCase.assertEquals(parseInput(expected), stagedDocument.text)
  }
}
