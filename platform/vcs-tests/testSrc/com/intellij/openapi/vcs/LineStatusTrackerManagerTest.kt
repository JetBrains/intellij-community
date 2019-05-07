// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.idea.Bombed
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker
import java.util.*

class LineStatusTrackerManagerTest : BaseLineStatusTrackerManagerTest() {
  private val FILE_1 = "file1.txt"
  private val FILE_2 = "file2.txt"
  private val FILE_3 = "file3.txt"

  fun `test partial tracker lifecycle - editor for unchanged file`() {
    createChangelist("Test")
    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    file.assertNullTracker()

    file.withOpenedEditor {
      val tracker = file.tracker
      assertNotNull(tracker)
      assertTrue(tracker is SimpleLocalLineStatusTracker)
    }
    file.assertNullTracker()
  }

  fun `test partial tracker lifecycle - editor for modified file`() {
    createChangelist("Test")
    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b1_c_d1_e")
    refreshCLM()
    file.assertNullTracker()

    file.withOpenedEditor {
      val tracker = file.tracker
      assertNotNull(tracker)
      assertTrue(tracker is PartialLocalLineStatusTracker)
    }
    assertNotNull(file.tracker)

    lstm.waitUntilBaseContentsLoaded()
    file.assertNullTracker()

    file.withOpenedEditor {
      val tracker = file.tracker
      assertNotNull(tracker)
      assertTrue(tracker is PartialLocalLineStatusTracker)

      lstm.waitUntilBaseContentsLoaded()
      assertNotNull(file.tracker)
    }
    file.assertNullTracker()
  }

  fun `test partial tracker lifecycle - multiple editors`() {
    createChangelist("Test")
    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b1_c_d1_e")
    refreshCLM()
    file.assertNullTracker()

    val requester1 = Any()
    val requester2 = Any()

    lstm.requestTrackerFor(file.document, requester1)
    assertNotNull(file.tracker)

    lstm.waitUntilBaseContentsLoaded()
    lstm.requestTrackerFor(file.document, requester2)
    assertNotNull(file.tracker)

    lstm.releaseTrackerFor(file.document, requester1)
    assertNotNull(file.tracker)

    lstm.releaseTrackerFor(file.document, requester2)
    file.assertNullTracker()
  }

  fun `test partial tracker lifecycle - with partial changes without editor`() {
    createChangelist("Test")
    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b1_c_d1_e")
    refreshCLM()
    file.assertNullTracker()

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker
      lstm.waitUntilBaseContentsLoaded()

      val ranges = tracker.getRanges()!!
      assertEquals(2, ranges.size)
      tracker.moveToChangelist(ranges[0], "Test".asListNameToList())
      tracker.assertAffectedChangeLists(DEFAULT, "Test")
    }
    assertNotNull(file.tracker)

    clm.waitUntilRefreshed()
    releaseUnneededTrackers()
    assertNotNull(file.tracker)

    file.moveAllChangesTo("Test")
    clm.waitUntilRefreshed()
    releaseUnneededTrackers() // partial tracker is not released immediately after becoming redundant
    file.assertNullTracker()
  }

  fun `test tracker from non-default changelist`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b1_c_d1_e")
    refreshCLM()

    file.moveAllChangesTo("Test")
    clm.waitUntilRefreshed()

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker
      lstm.waitUntilBaseContentsLoaded()

      tracker.assertAffectedChangeLists("Test")
    }
  }

  fun `test tracker from non-default changelist - modified during initialisation`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d1_e")
    refreshCLM()

    file.moveAllChangesTo("Test")
    clm.waitUntilRefreshed()

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker
      runCommand { tracker.document.replaceString(0, 1, "a2") }

      lstm.waitUntilBaseContentsLoaded()

      tracker.assertAffectedChangeLists("Test", DEFAULT)
    }
  }

  fun `test tracker from non-default changelist - closed file modified during initialisation, edit unchanged line`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d1_e")
    refreshCLM()

    file.moveAllChangesTo("Test")
    clm.waitUntilRefreshed()
    file.assertNullTracker()

    runCommand { file.document.replaceString(0, 1, "a2") }
    lstm.waitUntilBaseContentsLoaded()

    val tracker = file.tracker as PartialLocalLineStatusTracker
    val ranges = tracker.getRanges()!!
    assertEquals(2, ranges.size)
    ranges[0].assertChangeList(DEFAULT)
    ranges[1].assertChangeList("Test")
  }

  @Bombed(month = Calendar.MAY, day = 1, user = "Aleksey.Pivovarov")
  fun `test tracker from non-default changelist - closed file modified during initialisation, edit line from non-active list`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a1_b_c_d_e")
    refreshCLM()

    file.moveAllChangesTo("Test")
    clm.waitUntilRefreshed()
    file.assertNullTracker()

    runCommand { file.document.replaceString(0, 1, "a2") }
    lstm.waitUntilBaseContentsLoaded()

    file.assertAffectedChangeLists("Test")
  }

  @Bombed(month = Calendar.MAY, day = 1, user = "Aleksey.Pivovarov")
  fun `test tracker from non-default changelist - closed file modified during initialisation, edit unchanged line and line from non-active list`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a1_b_c_d_e1")
    setBaseVersion(FILE_1, "a_b_c_d_e")
    refreshCLM()

    file.moveAllChangesTo("Test")
    clm.waitUntilRefreshed()
    file.assertNullTracker()

    runCommand { file.document.replaceString(1, 2, "2") }
    runCommand { file.document.replaceString(5, 6, "c1") }
    lstm.waitUntilBaseContentsLoaded()

    val tracker = file.tracker as PartialLocalLineStatusTracker
    val ranges = tracker.getRanges()!!
    assertEquals(3, ranges.size)
    ranges[0].assertChangeList("Test")
    ranges[1].assertChangeList(DEFAULT)
    ranges[2].assertChangeList("Test")
  }

  fun `test tracker changes moves`() {
    createChangelist("Test #1")
    createChangelist("Test #2")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b1_c_d1_e")
    refreshCLM()

    file.moveAllChangesTo("Test #2")
    file.assertNullTracker()

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker
      lstm.waitUntilBaseContentsLoaded()
      assertEquals(2, tracker.getRanges()!!.size)
      tracker.assertAffectedChangeLists("Test #2")

      file.moveAllChangesTo("Test #1")
      tracker.assertAffectedChangeLists("Test #1")

      tracker.moveToChangelist(tracker.getRanges()!![0], "Test #2".asListNameToList())
      tracker.assertAffectedChangeLists("Test #1", "Test #2")

      file.moveChanges("Test #2", DEFAULT)
      tracker.assertAffectedChangeLists("Test #1", DEFAULT)

      file.moveAllChangesTo("Test #2")
      tracker.assertAffectedChangeLists("Test #2")
    }
  }

  fun `test tracker when file is moved on top of another file`() {
    createChangelist("Test #1")
    createChangelist("Test #2")

    val file1 = addLocalFile(FILE_1, "a_b_c_d_e")
    val file2 = addLocalFile(FILE_2, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a1_b_c_d_e1")
    setBaseVersion(FILE_2, "a1_b_c_d_e1")
    refreshCLM()


    file1.withOpenedEditor {
      val tracker = file1.tracker as PartialLocalLineStatusTracker
      lstm.waitUntilBaseContentsLoaded()

      val ranges = tracker.getRanges()!!
      tracker.moveToChangelist(ranges[0], "Test #2".asListNameToList())
    }

    file2.withOpenedEditor {
      val tracker = file2.tracker as PartialLocalLineStatusTracker
      lstm.waitUntilBaseContentsLoaded()

      val ranges = tracker.getRanges()!!
      tracker.moveToChangelist(ranges[1], "Test #2".asListNameToList())
    }

    releaseUnneededTrackers()
    assertNotNull(file1.tracker)
    assertNotNull(file2.tracker)

    runWriteAction {
      file1.delete(this)
      file2.rename(this, file1.name)
    }
    file1.assertNullTracker()

    refreshCLM()
    lstm.waitUntilBaseContentsLoaded()

    file2.moveAllChangesTo("Test #1")
    releaseUnneededTrackers()
    file2.assertNullTracker()
  }

  fun `test tracker changes moves - empty tracker`() {
    createChangelist("Test #1")
    createChangelist("Test #2")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e")
    refreshCLM()

    file.moveAllChangesTo("Test #2")

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker
      lstm.waitUntilBaseContentsLoaded()
      assertEquals(0, tracker.getRanges()!!.size)
      tracker.assertAffectedChangeLists("Test #2")

      file.moveAllChangesTo("Test #1")
      tracker.assertAffectedChangeLists("Test #1")

      file.moveAllChangesTo("Test #2")
      tracker.assertAffectedChangeLists("Test #2")

      runCommand { tracker.document.replaceString(0, 1, "a2") }
      tracker.assertAffectedChangeLists(DEFAULT)
    }
  }

  fun `test tracker before initialisation - no changed lines in vcs, local changes reverted`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e")
    refreshCLM()
    file.moveAllChangesTo("Test")

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker

      tracker.assertAffectedChangeLists("Test")

      runCommand { tracker.document.replaceString(0, 1, "a2") }
      tracker.assertAffectedChangeLists(DEFAULT, "Test")

      runCommand { tracker.document.replaceString(0, 2, "a") }
      tracker.assertAffectedChangeLists("Test")

      assertFalse(tracker.isOperational())
      assertNull(tracker.getRanges())

      lstm.waitUntilBaseContentsLoaded()
      assertEquals(0, tracker.getRanges()!!.size)
      tracker.assertAffectedChangeLists("Test")
    }
  }

  fun `test tracker before initialisation - typing`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e2")
    refreshCLM()
    file.moveAllChangesTo("Test")

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker

      tracker.assertAffectedChangeLists("Test")

      runCommand { tracker.document.replaceString(0, 1, "a2") }
      tracker.assertAffectedChangeLists(DEFAULT, "Test")

      assertFalse(tracker.isOperational())
      assertNull(tracker.getRanges())

      lstm.waitUntilBaseContentsLoaded()
      assertEquals(2, tracker.getRanges()!!.size)
      tracker.assertAffectedChangeLists(DEFAULT, "Test")
    }
  }

  fun `test tracker before initialisation - no changed lines in vcs`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e")
    refreshCLM()
    file.moveAllChangesTo("Test")

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker

      tracker.assertAffectedChangeLists("Test")

      runCommand { tracker.document.replaceString(0, 1, "a2") }
      tracker.assertAffectedChangeLists(DEFAULT, "Test")

      assertFalse(tracker.isOperational())
      assertNull(tracker.getRanges())

      lstm.waitUntilBaseContentsLoaded()
      assertEquals(1, tracker.getRanges()!!.size)
      tracker.assertAffectedChangeLists(DEFAULT)
    }
  }

  fun `test tracker before initialisation - old local changes reverted`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a2_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e")
    refreshCLM()
    file.moveAllChangesTo("Test")

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker

      tracker.assertAffectedChangeLists("Test")

      runCommand { tracker.document.replaceString(0, 2, "a") }
      tracker.assertAffectedChangeLists(DEFAULT, "Test")

      assertFalse(tracker.isOperational())
      assertNull(tracker.getRanges())

      lstm.waitUntilBaseContentsLoaded()
      assertEquals(0, tracker.getRanges()!!.size)
      tracker.assertAffectedChangeLists(DEFAULT)
    }
  }

  fun `test bulk refresh freezes tracker - inner operation`() {
    val file = addLocalFile(FILE_1, "a_b_c_d_e2")
    setBaseVersion(FILE_1, "a_b_c_d_e")
    refreshCLM()

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker
      lstm.waitUntilBaseContentsLoaded()
      assertTrue(tracker.isValid())

      runBatchFileChangeOperation {
        assertFalse(tracker.isValid())
      }
      assertTrue(tracker.isValid())
    }
    file.assertNullTracker()
  }

  fun `test bulk refresh freezes tracker - outer operation`() {
    val file = addLocalFile(FILE_1, "a_b_c_d_e2")
    setBaseVersion(FILE_1, "a_b_c_d_e")
    refreshCLM()

    runBatchFileChangeOperation {
      file.withOpenedEditor {
        val tracker = file.tracker as PartialLocalLineStatusTracker
        lstm.waitUntilBaseContentsLoaded()
        assertFalse(tracker.isValid())
      }
    }
    file.assertNullTracker()
  }

  fun `test bulk refresh freezes tracker - interleaving operation`() {
    val file = addLocalFile(FILE_1, "a_b_c_d_e2")
    setBaseVersion(FILE_1, "a_b_c_d_e")
    refreshCLM()

    runBatchFileChangeOperation {
      lstm.requestTrackerFor(file.document, this)
      lstm.waitUntilBaseContentsLoaded()

      val tracker = file.tracker as PartialLocalLineStatusTracker
      assertFalse(tracker.isValid())
    }
    val tracker = file.tracker as PartialLocalLineStatusTracker
    assertTrue(tracker.isValid())
    lstm.releaseTrackerFor(file.document, this)
    file.assertNullTracker()
  }

  fun `test bulk refresh freezes tracker - multiple operations`() {
    val file = addLocalFile(FILE_1, "a_b_c_d_e2")
    setBaseVersion(FILE_1, "a_b_c_d_e")
    refreshCLM()

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker
      lstm.waitUntilBaseContentsLoaded()
      assertTrue(tracker.isValid())

      runBatchFileChangeOperation {
        runBatchFileChangeOperation {
          assertFalse(tracker.isValid())
        }
        assertFalse(tracker.isValid())
      }
      assertTrue(tracker.isValid())
    }
    file.assertNullTracker()
  }

  fun `test vcs refresh - incoming changes in non-active changelist 1`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e2")
    setBaseVersion(FILE_1, "a_b_c_d_e")
    refreshCLM()
    file.moveAllChangesTo("Test")
    file.assertAffectedChangeLists("Test")

    runCommand { file.document.replaceString(0, 1, "a2") }
    val tracker = file.tracker as PartialLocalLineStatusTracker
    tracker.assertAffectedChangeLists(DEFAULT, "Test")

    setBaseVersion(FILE_1, "a2_b_c_d_e")
    refreshCLM()

    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    file.assertNullTracker()
    file.assertAffectedChangeLists("Test")
  }

  fun `test vcs refresh - incoming changes in non-active changelist 2`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e2")
    setBaseVersion(FILE_1, "a_b_c_d_e")
    refreshCLM()
    file.moveAllChangesTo("Test")
    file.assertAffectedChangeLists("Test")

    runCommand { file.document.replaceString(0, 1, "a2") }
    lstm.waitUntilBaseContentsLoaded()

    val tracker = file.tracker as PartialLocalLineStatusTracker
    tracker.assertAffectedChangeLists(DEFAULT, "Test")

    setBaseVersion(FILE_1, "a2_b_c_d_e")
    refreshCLM()

    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    file.assertNullTracker()
    file.assertAffectedChangeLists("Test")
  }

  fun `test tracker initialisation does not disrupt command group`() {
    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    val document = file.document
    file.assertNullTracker()

    fun typing(text: String) {
      runCommand("typing") {
        document.insertString(0, text)
      }
    }

    typing("a")
    typing("a")
    typing("a")

    file.withOpenedEditor {
      typing("b")
      typing("b")
      typing("b")

      setBaseVersion(FILE_1, "a_b_c_d_e2")
      refreshCLM()

      typing("c")
      typing("c")
      typing("c")

      lstm.waitUntilBaseContentsLoaded()
      file.tracker!!.assertBaseTextContentIs("a_b_c_d_e2")

      typing("d")
      typing("d")
      typing("d")

      undo(document)
      file.tracker!!.assertTextContentIs("a_b_c_d_e")
      file.tracker!!.assertBaseTextContentIs("a_b_c_d_e2")

      redo(document)
      file.tracker!!.assertTextContentIs("dddcccbbbaaaa_b_c_d_e")
      file.tracker!!.assertBaseTextContentIs("a_b_c_d_e2")
    }
  }

  fun `test vcs refresh - duplicated copies`() {
    createChangelist("Test")

    val file1 = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e2", FILE_3)
    refreshCLM()
    file1.moveAllChangesTo("Test")

    assertEquals(1, clm.allChanges.size)
    assertEquals(Change.Type.MOVED, clm.getChange(FILE_1.toFilePath)?.type)
    file1.assertAffectedChangeLists("Test")

    // FILE_2 is moved to "Test", because 'guessChangeListByPaths' finds FILE_3 (for FILE_1 change) is in this changelist.
    // Probably, we should detect such cases and leave change in default changelist
    // see `test vcs refresh - duplicated copies with tracker`
    val file2 = addLocalFile(FILE_2, "a_b_c_d_e")
    setBaseVersion(FILE_2, "a_b_c_d_e2", FILE_3)
    refreshCLM()

    assertEquals(2, clm.allChanges.size)
    assertEquals(Change.Type.NEW, clm.getChange(FILE_1.toFilePath)?.type)
    assertEquals(Change.Type.MOVED, clm.getChange(FILE_2.toFilePath)?.type)
    file1.assertAffectedChangeLists("Test")
    file2.assertAffectedChangeLists("Test")
  }

  fun `test vcs refresh - duplicated copies with tracker`() {
    createChangelist("Test")

    val file1 = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e2", FILE_3)
    refreshCLM()
    file1.moveAllChangesTo("Test")
    file1.assertAffectedChangeLists("Test")

    assertEquals(1, clm.allChanges.size)
    assertEquals(Change.Type.MOVED, clm.getChange(FILE_1.toFilePath)?.type)

    file1.withOpenedEditor {
      // FILE_2 is NOT moved to "Test", because FILE_1 has an assigned tracker and is not used for 'guessChangeListByPaths' detection
      // (see `test vcs refresh - duplicated copies`)
      val file2 = addLocalFile(FILE_2, "a_b_c_d_e")
      setBaseVersion(FILE_2, "a_b_c_d_e2", FILE_3)
      refreshCLM()

      assertEquals(2, clm.allChanges.size)
      assertEquals(Change.Type.NEW, clm.getChange(FILE_1.toFilePath)?.type)
      assertEquals(Change.Type.MOVED, clm.getChange(FILE_2.toFilePath)?.type)
      file1.assertAffectedChangeLists("Test")
      file2.assertAffectedChangeLists(DEFAULT)
    }

    releaseUnneededTrackers()
    FILE_1.toFilePath.assertAffectedChangeLists("Test")
    FILE_2.toFilePath.assertAffectedChangeLists(DEFAULT)
  }


  fun `test vcs refresh - tracker released during update (editor closed)`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e2")
    refreshCLM()
    file.moveAllChangesTo("Test")
    file.assertAffectedChangeLists("Test")

    val requester = Any()
    lstm.requestTrackerFor(file.document, requester)
    lstm.waitUntilBaseContentsLoaded()
    assertNotNull(file.tracker)
    assertTrue(file.tracker is PartialLocalLineStatusTracker)

    setBaseVersion(FILE_1, "a_b_c_d_e3")
    changeProvider.awaitAndBlockRefresh().use {
      lstm.releaseTrackerFor(file.document, requester)
    }

    file.assertAffectedChangeLists("Test")

    clm.waitUntilRefreshed()
    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    file.assertNullTracker()
    file.assertAffectedChangeLists("Test")
  }

  fun `test vcs refresh - tracker released during update (no more partial changes)`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e2")
    refreshCLM()
    file.moveAllChangesTo("Test")
    file.assertAffectedChangeLists("Test")

    runCommand { file.document.replaceString(0, 1, "a2") }
    lstm.waitUntilBaseContentsLoaded()

    assertNotNull(file.tracker)
    assertTrue(file.tracker is PartialLocalLineStatusTracker)
    file.assertAffectedChangeLists("Test", DEFAULT)

    setBaseVersion(FILE_1, "a_b_c_d_e3")
    changeProvider.awaitAndBlockRefresh().use {
      runCommand { file.document.replaceString(0, 2, "a") }
      releaseUnneededTrackers()
    }

    file.assertAffectedChangeLists("Test")

    clm.waitUntilRefreshed()
    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    file.assertNullTracker()
    file.assertAffectedChangeLists("Test")
  }

  fun `test vcs refresh - tracker created during update`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e2")
    refreshCLM()
    file.moveAllChangesTo("Test")
    file.assertAffectedChangeLists("Test")

    setBaseVersion(FILE_1, "a_b_c_d_e3")
    file.assertAffectedChangeLists("Test")

    changeProvider.awaitAndBlockRefresh().use {
      runCommand { file.document.replaceString(0, 1, "a2") }
      lstm.waitUntilBaseContentsLoaded()

      assertNotNull(file.tracker)
      assertTrue(file.tracker is PartialLocalLineStatusTracker)
      file.assertAffectedChangeLists("Test", DEFAULT)
    }

    clm.waitUntilRefreshed()

    file.assertAffectedChangeLists("Test", DEFAULT)
  }

  fun `test vcs refresh - tracker created during update (changes moved via LST)`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e2")
    refreshCLM()
    file.moveAllChangesTo("Test")
    file.assertAffectedChangeLists("Test")

    setBaseVersion(FILE_1, "a_b_c_d_e3")
    changeProvider.awaitAndBlockRefresh().use {
      runCommand { file.document.replaceString(0, 1, "a2") }
      lstm.waitUntilBaseContentsLoaded()

      val tracker = file.tracker as PartialLocalLineStatusTracker
      file.assertAffectedChangeLists("Test", DEFAULT)

      val range = tracker.getRanges()!![1]
      tracker.moveToChangelist(range, DEFAULT.asListNameToList())
      file.assertAffectedChangeLists(DEFAULT)
      assertNotNull(file.tracker)
    }
    assertNotNull(file.tracker)

    clm.waitUntilRefreshed()
    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    file.assertNullTracker()
    file.assertAffectedChangeLists(DEFAULT)
  }

  fun `test vcs refresh - tracker created during update (changes moved via CLM)`() {
    createChangelist("Test")
    createChangelist("Test 2")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e2")
    refreshCLM()
    file.moveAllChangesTo("Test")
    file.assertAffectedChangeLists("Test")

    setBaseVersion(FILE_1, "a_b_c_d_e3")
    changeProvider.awaitAndBlockRefresh().use {
      runCommand { file.document.replaceString(0, 1, "a2") }
      lstm.waitUntilBaseContentsLoaded()

      val tracker = file.tracker as PartialLocalLineStatusTracker
      file.assertAffectedChangeLists("Test", DEFAULT)

      val range = tracker.getRanges()!![1]
      file.moveAllChangesTo("Test 2")
      file.assertAffectedChangeLists("Test 2")
      assertNotNull(file.tracker)
    }
    assertNotNull(file.tracker)

    clm.waitUntilRefreshed()
    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    file.assertNullTracker()
    file.assertAffectedChangeLists("Test 2")
  }

  @Bombed(year = 3000, month = Calendar.JANUARY, day = 1, user = "Aleksey.Pivovarov")
  fun `test vcs refresh - tracker created and released during update (changes moved via LST)`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e2")
    refreshCLM()
    file.moveAllChangesTo("Test")
    file.assertAffectedChangeLists("Test")

    setBaseVersion(FILE_1, "a_b_c_d_e3")
    changeProvider.awaitAndBlockRefresh().use {
      runCommand { file.document.replaceString(0, 1, "a2") }
      lstm.waitUntilBaseContentsLoaded()

      val tracker = file.tracker as PartialLocalLineStatusTracker
      file.assertAffectedChangeLists("Test", DEFAULT)

      val range = tracker.getRanges()!![1]
      tracker.moveToChangelist(range, DEFAULT.asListNameToList())
      file.assertAffectedChangeLists(DEFAULT)

      releaseUnneededTrackers()
      file.assertNullTracker()
    }
    file.assertNullTracker()

    clm.waitUntilRefreshed()
    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    file.assertNullTracker()
    file.assertAffectedChangeLists(DEFAULT)
  }

  fun `test vcs refresh - tracker created and released during update (changes moved via CLM)`() {
    createChangelist("Test")
    createChangelist("Test 2")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e2")
    refreshCLM()
    file.moveAllChangesTo("Test")
    file.assertAffectedChangeLists("Test")

    setBaseVersion(FILE_1, "a_b_c_d_e3")
    changeProvider.awaitAndBlockRefresh().use {
      runCommand { file.document.replaceString(0, 1, "a2") }
      lstm.waitUntilBaseContentsLoaded()

      val tracker = file.tracker as PartialLocalLineStatusTracker
      file.assertAffectedChangeLists("Test", DEFAULT)

      val range = tracker.getRanges()!![1]
      file.moveAllChangesTo("Test 2")
      file.assertAffectedChangeLists("Test 2")

      releaseUnneededTrackers()
      file.assertNullTracker()
    }
    file.assertNullTracker()

    clm.waitUntilRefreshed()
    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    file.assertNullTracker()
    file.assertAffectedChangeLists("Test 2")
  }

  fun `test vcs refresh - tracker created during update (and released after update)`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e2")
    refreshCLM()
    file.moveAllChangesTo("Test")
    file.assertAffectedChangeLists("Test")

    setBaseVersion(FILE_1, "a_b_c_d_e3")
    changeProvider.awaitAndBlockRefresh().use {
      runCommand { file.document.replaceString(0, 1, "a2") }
      lstm.waitUntilBaseContentsLoaded()

      val tracker = file.tracker as PartialLocalLineStatusTracker
      file.assertAffectedChangeLists("Test", DEFAULT)

      val range = tracker.getRanges()!![0]
      tracker.moveToChangelist(range, "Test".asListNameToList())
      file.assertAffectedChangeLists("Test")
    }
    assertNotNull(file.tracker)

    clm.waitUntilRefreshed()
    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    file.assertNullTracker()
    file.assertAffectedChangeLists("Test")
  }

  @Bombed(year = 3000, month = Calendar.JANUARY, day = 1, user = "Aleksey.Pivovarov")
  fun `test vcs refresh - tracker created and released during update  (changes moved via LST, no partial changes)`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e2")
    refreshCLM()
    file.moveAllChangesTo("Test")
    file.assertAffectedChangeLists("Test")

    setBaseVersion(FILE_1, "a_b_c_d_e3")
    changeProvider.awaitAndBlockRefresh().use {
      file.withOpenedEditor {
        lstm.waitUntilBaseContentsLoaded()

        val tracker = file.tracker as PartialLocalLineStatusTracker
        file.assertAffectedChangeLists("Test")

        val range = tracker.getRanges()!![0]
        tracker.moveToChangelist(range, DEFAULT.asListNameToList())
        file.assertAffectedChangeLists(DEFAULT)
      }
    }
    file.assertNullTracker()

    clm.waitUntilRefreshed()
    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    file.assertNullTracker()
    file.assertAffectedChangeLists(DEFAULT)
  }

  fun `test file rename - no partial changes`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e2")
    refreshCLM()
    file.moveAllChangesTo("Test")
    file.assertAffectedChangeLists("Test")

    runWriteAction {
      file.rename(this, FILE_2)
    }
    setBaseVersion(FILE_2, "a_b_c_d_e2", FILE_1)
    refreshCLM()
    file.assertAffectedChangeLists("Test")
  }

  fun `test file rename - with partial changes`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e2")
    refreshCLM()
    file.moveAllChangesTo("Test")
    runCommand { file.document.replaceString(0, 1, "a2") }
    FILE_1.toFilePath.assertAffectedChangeLists("Test", DEFAULT)

    lstm.waitUntilBaseContentsLoaded()
    FILE_1.toFilePath.assertAffectedChangeLists("Test", DEFAULT)

    runWriteAction {
      file.rename(this, FILE_2)
    }
    removeBaseVersion(FILE_1)
    setBaseVersion(FILE_2, "a_b_c_d_e2", FILE_1)
    refreshCLM()
    FILE_2.toFilePath.assertAffectedChangeLists("Test", DEFAULT)
  }

  fun `test file rename - with partial changes, while tracker not initialized`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e2")
    refreshCLM()
    file.moveAllChangesTo("Test")
    runCommand { file.document.replaceString(0, 1, "a2") }
    FILE_1.toFilePath.assertAffectedChangeLists("Test", DEFAULT)

    runWriteAction {
      file.rename(this, FILE_2)
    }
    removeBaseVersion(FILE_1)
    setBaseVersion(FILE_2, "a_b_c_d_e2", FILE_1)
    refreshCLM()

    // It might be better to return ("Test", DEFAULT) here, but the case is tricky
    FILE_2.toFilePath.assertAffectedChangeLists(DEFAULT)
  }

  @Bombed(year = 3000, month = Calendar.JANUARY, day = 1, user = "Aleksey.Pivovarov")
  fun `test file rename - with partial changes, try release tracker during CLM refresh`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e2")
    refreshCLM()
    file.moveAllChangesTo("Test")
    runCommand { file.document.replaceString(0, 1, "a2") }
    FILE_1.toFilePath.assertAffectedChangeLists("Test", DEFAULT)

    lstm.waitUntilBaseContentsLoaded()
    FILE_1.toFilePath.assertAffectedChangeLists("Test", DEFAULT)

    runWriteAction {
      file.rename(this, FILE_2)
    }
    removeBaseVersion(FILE_1)
    setBaseVersion(FILE_2, "a_b_c_d_e2", FILE_1)

    changeProvider.awaitAndBlockRefresh().use {
      file.withOpenedEditor {
        lstm.waitUntilBaseContentsLoaded()
        releaseUnneededTrackers()

        FILE_1.toFilePath.assertAffectedChangeLists("Test", DEFAULT)
      }
    }

    clm.waitUntilRefreshed()
    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    FILE_2.toFilePath.assertAffectedChangeLists("Test", DEFAULT)
  }

  fun `test shelve-unshelve 1`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a1_b_c_d_e_f1")
    setBaseVersion(FILE_1, "a_b_c_d_e_f")
    refreshCLM()
    file.moveAllChangesTo("Test")
    runCommand { file.document.replaceString(7, 8, "d2") }
    FILE_1.toFilePath.assertAffectedChangeLists("Test", DEFAULT)

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker

      lstm.waitUntilBaseContentsLoaded()
      FILE_1.toFilePath.assertAffectedChangeLists("Test", DEFAULT)

      val list = clm.findChangeList(DEFAULT)!!
      val shelvedList = shelveManager.shelveChanges(list.changes, "X", true)
      tracker.assertTextContentIs("a1_b_c_d_e_f1")
      FILE_1.toFilePath.assertAffectedChangeLists("Test")

      shelveManager.unshelveChangeList(shelvedList, null, null, list, false)
      tracker.assertTextContentIs("a1_b_c_d2_e_f1")
      FILE_1.toFilePath.assertAffectedChangeLists("Test", DEFAULT)
    }
  }

  fun `test shelve-unshelve 2`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a1_b_c_d_e_f1")
    setBaseVersion(FILE_1, "a_b_c_d_e_f")
    refreshCLM()
    file.moveAllChangesTo("Test")
    runCommand { file.document.replaceString(7, 8, "d2") }
    FILE_1.toFilePath.assertAffectedChangeLists("Test", DEFAULT)

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker

      lstm.waitUntilBaseContentsLoaded()
      FILE_1.toFilePath.assertAffectedChangeLists("Test", DEFAULT)

      val list = clm.findChangeList("Test")!!
      val shelvedList = shelveManager.shelveChanges(list.changes, "X", true)
      tracker.assertTextContentIs("a_b_c_d2_e_f")
      FILE_1.toFilePath.assertAffectedChangeLists(DEFAULT)

      shelveManager.unshelveChangeList(shelvedList, null, null, list, false)
      tracker.assertTextContentIs("a1_b_c_d2_e_f1")
      FILE_1.toFilePath.assertAffectedChangeLists("Test", DEFAULT)
    }
  }
}