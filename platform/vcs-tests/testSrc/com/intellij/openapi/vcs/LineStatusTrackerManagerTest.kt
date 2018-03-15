// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vcs.BaseLineStatusTrackerTestCase.Companion.assertEqualRanges
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.Range
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker
import com.intellij.openapi.vcs.impl.LineStatusTrackerSettingListener

class LineStatusTrackerManagerTest : BaseLineStatusTrackerManagerTest() {
  private val FILE_1 = "file1.txt"
  private val FILE_2 = "file2.txt"

  fun `test mock changes`() {
    setBaseVersion(FILE_1, "oldText")
    refreshCLM()
    assertEquals(1, clm.allChanges.size)
    assertEquals(Change.Type.DELETED, clm.allChanges.first().type)

    addLocalFile(FILE_1, "text")
    setBaseVersion(FILE_1, null)
    refreshCLM()
    assertEquals(1, clm.allChanges.size)
    assertEquals(Change.Type.NEW, clm.allChanges.first().type)

    setBaseVersion(FILE_1, "oldText")
    refreshCLM()
    assertEquals(1, clm.allChanges.size)
    assertEquals(Change.Type.MODIFICATION, clm.allChanges.first().type)

    removeLocalFile(FILE_1)
    refreshCLM()
    assertEquals(1, clm.allChanges.size)
    assertEquals(Change.Type.DELETED, clm.allChanges.first().type)

    removeBaseVersion(FILE_1)
    refreshCLM()
    assertEquals(0, clm.allChanges.size)

    setBaseVersion(FILE_1, "oldText")
    setBaseVersion(FILE_2, "oldText")
    refreshCLM()
    assertEquals(2, clm.allChanges.size)
  }

  fun `test partial tracker lifecycle - editor for unchanged file`() {
    createChangelist("Test")
    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    assertNull(file.tracker)

    file.withOpenedEditor {
      val tracker = file.tracker
      assertNotNull(tracker)
      assertTrue(tracker is SimpleLocalLineStatusTracker)
    }
    assertNull(file.tracker)
  }

  fun `test partial tracker lifecycle - editor for modified file`() {
    createChangelist("Test")
    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b1_c_d1_e")
    refreshCLM()
    assertNull(file.tracker)

    file.withOpenedEditor {
      val tracker = file.tracker
      assertNotNull(tracker)
      assertTrue(tracker is PartialLocalLineStatusTracker)
    }
    assertNotNull(file.tracker)

    lstm.waitUntilBaseContentsLoaded()
    assertNull(file.tracker)

    file.withOpenedEditor {
      val tracker = file.tracker
      assertNotNull(tracker)
      assertTrue(tracker is PartialLocalLineStatusTracker)

      lstm.waitUntilBaseContentsLoaded()
      assertNotNull(file.tracker)
    }
    assertNull(file.tracker)
  }

  fun `test partial tracker lifecycle - editor for modified file 2`() {
    createChangelist("Test")
    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    assertNull(file.tracker)

    file.withOpenedEditor {
      val simpleTracker = file.tracker as SimpleLocalLineStatusTracker

      setBaseVersion(FILE_1, "a_b_c_d_e")
      refreshCLM()
      fileStatusManager.fileStatusesChanged()

      val partialTracker = file.tracker
      assertTrue(simpleTracker.isReleased)
      assertNotNull(partialTracker)
      assertTrue(partialTracker is PartialLocalLineStatusTracker)

      lstm.waitUntilBaseContentsLoaded()
    }
    assertNull(file.tracker)
  }

  fun `test partial tracker lifecycle - multiple editors`() {
    createChangelist("Test")
    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b1_c_d1_e")
    refreshCLM()
    assertNull(file.tracker)

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
    assertNull(file.tracker)
  }

  fun `test partial tracker lifecycle - with partial changes without editor`() {
    createChangelist("Test")
    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b1_c_d1_e")
    refreshCLM()
    assertNull(file.tracker)

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker
      lstm.waitUntilBaseContentsLoaded()

      val ranges = tracker.getRanges()!!
      assertEquals(2, ranges.size)
      tracker.moveToChangelist(ranges[0], "Test".asListNameToList())
      tracker.assertAffectedChangeLists("Default", "Test")
    }
    assertNotNull(file.tracker)

    clm.waitUntilRefreshed()
    releaseUnneededTrackers()
    assertNotNull(file.tracker)

    file.moveAllChangesTo("Test")
    clm.waitUntilRefreshed()
    releaseUnneededTrackers() // partial tracker is not released immediately after becoming redundant
    assertNull(file.tracker)
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

      tracker.assertAffectedChangeLists("Test", "Default")
    }
  }

  fun `test tracker changes moves`() {
    createChangelist("Test #1")
    createChangelist("Test #2")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b1_c_d1_e")
    refreshCLM()

    file.moveAllChangesTo("Test #2")
    assertNull(file.tracker)

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker
      lstm.waitUntilBaseContentsLoaded()
      assertEquals(2, tracker.getRanges()!!.size)
      tracker.assertAffectedChangeLists("Test #2")

      file.moveAllChangesTo("Test #1")
      tracker.assertAffectedChangeLists("Test #1")

      tracker.moveToChangelist(tracker.getRanges()!![0], "Test #2".asListNameToList())
      tracker.assertAffectedChangeLists("Test #1", "Test #2")

      file.moveChanges("Test #2", "Default")
      tracker.assertAffectedChangeLists("Test #1", "Default")

      file.moveAllChangesTo("Test #2")
      tracker.assertAffectedChangeLists("Test #2")
    }
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
      tracker.assertAffectedChangeLists("Default")
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
      tracker.assertAffectedChangeLists("Default", "Test")

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
      tracker.assertAffectedChangeLists("Default", "Test")

      assertFalse(tracker.isOperational())
      assertNull(tracker.getRanges())

      lstm.waitUntilBaseContentsLoaded()
      assertEquals(2, tracker.getRanges()!!.size)
      tracker.assertAffectedChangeLists("Default", "Test")
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
      tracker.assertAffectedChangeLists("Default", "Test")

      assertFalse(tracker.isOperational())
      assertNull(tracker.getRanges())

      lstm.waitUntilBaseContentsLoaded()
      assertEquals(1, tracker.getRanges()!!.size)
      tracker.assertAffectedChangeLists("Default")
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
      tracker.assertAffectedChangeLists("Default", "Test")

      assertFalse(tracker.isOperational())
      assertNull(tracker.getRanges())

      lstm.waitUntilBaseContentsLoaded()
      assertEquals(0, tracker.getRanges()!!.size)
      tracker.assertAffectedChangeLists("Default")
    }
  }

  fun `test partial tracker lifecycle - simple tracker passes ranges to partial one`() {
    createChangelist("Test")
    val file = addLocalFile(FILE_1, "a_a_a")
    assertNull(file.tracker)

    file.withOpenedEditor {
      val simpleTracker = file.tracker as SimpleLocalLineStatusTracker
      runCommand { simpleTracker.document.insertString(0, "a\n") }

      setBaseVersion(FILE_1, "a_a_a")
      refreshCLM()
      lstm.waitUntilBaseContentsLoaded()

      val partialTracker = file.tracker as PartialLocalLineStatusTracker
      assertEqualRanges(partialTracker.getRanges()!!, listOf(Range(0, 1, 0, 0)))
    }
    assertNull(file.tracker)
  }

  fun `test partial tracker lifecycle - simple tracker passes ranges to partial one 2`() {
    createChangelist("Test")
    val file = addLocalFile(FILE_1, "a_a_a")
    assertNull(file.tracker)

    file.withOpenedEditor {
      val simpleTracker = file.tracker as SimpleLocalLineStatusTracker
      runCommand { simpleTracker.document.insertString(2, "a\n") }

      setBaseVersion(FILE_1, "a_a_a")
      refreshCLM()
      lstm.waitUntilBaseContentsLoaded()

      val partialTracker = file.tracker as PartialLocalLineStatusTracker
      assertEqualRanges(partialTracker.getRanges()!!, listOf(Range(1, 2, 1, 1)))
    }
    assertNull(file.tracker)
  }

  fun `test partial tracker lifecycle - simple partial passes ranges to simple one`() {
    val file = addLocalFile(FILE_1, "a_a_a")
    setBaseVersion(FILE_1, "a_a_a")
    refreshCLM()
    assertNull(file.tracker)

    file.withOpenedEditor {
      val partialTracker = file.tracker as PartialLocalLineStatusTracker
      lstm.waitUntilBaseContentsLoaded()

      runCommand { partialTracker.document.insertString(2, "a\n") }

      VcsApplicationSettings.getInstance().ENABLE_PARTIAL_CHANGELISTS = false
      ApplicationManager.getApplication().messageBus.syncPublisher(LineStatusTrackerSettingListener.TOPIC).settingsUpdated()

      val simpleTracker = file.tracker as SimpleLocalLineStatusTracker
      lstm.waitUntilBaseContentsLoaded()
      assertEqualRanges(simpleTracker.getRanges()!!, listOf(Range(1, 2, 1, 1)))
    }
    assertNull(file.tracker)
  }
}