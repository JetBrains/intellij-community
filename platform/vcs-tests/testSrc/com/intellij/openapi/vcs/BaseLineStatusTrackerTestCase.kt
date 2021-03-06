// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.diff.util.Side
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.vcs.LineStatusTrackerTestUtil.parseInput
import com.intellij.openapi.vcs.ex.*
import com.intellij.openapi.vcs.ex.LocalLineStatusTracker.Mode
import com.intellij.testFramework.LightVirtualFile
import java.util.*

abstract class BaseLineStatusTrackerTestCase : BaseLineStatusTrackerManagerTest() {
  protected fun test(text: String, task: SimpleTest.() -> Unit) {
    test(text, text, false, task)
  }

  protected fun test(text: String, vcsText: String, smart: Boolean = false, task: SimpleTest.() -> Unit) {
    resetTestState()
    VcsApplicationSettings.getInstance().SHOW_WHITESPACES_IN_LST = smart
    arePartialChangelistsSupported = false

    doTest(text, vcsText, { tracker -> SimpleTest(tracker as SimpleLocalLineStatusTracker) }, task)
  }

  protected fun testPartial(text: String, task: PartialTest.() -> Unit) {
    testPartial(text, text, task)
  }

  protected fun testPartial(text: String, vcsText: String, task: PartialTest.() -> Unit) {
    resetTestState()

    doTest(text, vcsText, { tracker -> PartialTest(tracker as ChangelistsLocalLineStatusTracker) }, task)
  }

  private fun <TestHelper : TrackerModificationsTest> doTest(text: String, vcsText: String,
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

  protected fun lightTest(text: String, vcsText: String, smart: Boolean = false, task: SimpleTest.() -> Unit) {
    val file = LightVirtualFile("LSTTestFile", PlainTextFileType.INSTANCE, parseInput(text))
    val document = FileDocumentManager.getInstance().getDocument(file)!!
    val tracker = runWriteAction {
      val tracker = SimpleLocalLineStatusTracker.createTracker(getProject(), document, file)
      tracker.mode = Mode(true, true, smart)
      tracker.setBaseRevision(parseInput(vcsText))
      tracker
    }

    try {
      val testHelper = SimpleTest(tracker)
      testHelper.verify()
      task(testHelper)
      testHelper.verify()
    }
    finally {
      tracker.release()
    }
  }

  protected inner class SimpleTest(val simpleTracker: SimpleLocalLineStatusTracker) : TrackerModificationsTest(simpleTracker)

  protected inner class PartialTest(val partialTracker: ChangelistsLocalLineStatusTracker) : TrackerModificationsTest(partialTracker) {
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
}
