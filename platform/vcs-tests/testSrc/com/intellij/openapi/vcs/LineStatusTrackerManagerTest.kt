// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.idea.Bombed
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker
import java.util.*

class LineStatusTrackerManagerTest : BaseLineStatusTrackerManagerTest() {
  private val FILE_1 = "file1.txt"
  private val FILE_2 = "file2.txt"

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
    assertNull(file.tracker)
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
    assertNull(file.tracker)
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
    assertNull(file.tracker)
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
    assertNull(file.tracker)
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
    tracker.assertAffectedChangeLists("Default", "Test")

    setBaseVersion(FILE_1, "a2_b_c_d_e")
    refreshCLM()

    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    assertNull(file.tracker)
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
    tracker.assertAffectedChangeLists("Default", "Test")

    setBaseVersion(FILE_1, "a2_b_c_d_e")
    refreshCLM()

    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    assertNull(file.tracker)
    file.assertAffectedChangeLists("Test")
  }

  fun `test tracker initialisation does not disrupt command group`() {
    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    val document = file.document
    assertNull(file.tracker)

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

    assertNull(file.tracker)
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
    file.assertAffectedChangeLists("Test", "Default")

    setBaseVersion(FILE_1, "a_b_c_d_e3")
    changeProvider.awaitAndBlockRefresh().use {
      runCommand { file.document.replaceString(0, 2, "a") }
      releaseUnneededTrackers()
    }

    file.assertAffectedChangeLists("Test")

    clm.waitUntilRefreshed()
    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    assertNull(file.tracker)
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
      file.assertAffectedChangeLists("Test", "Default")
    }

    clm.waitUntilRefreshed()

    file.assertAffectedChangeLists("Test", "Default")
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
      file.assertAffectedChangeLists("Test", "Default")

      val range = tracker.getRanges()!![1]
      tracker.moveToChangelist(range, "Default".asListNameToList())
      file.assertAffectedChangeLists("Default")
      assertNotNull(file.tracker)
    }
    assertNotNull(file.tracker)

    clm.waitUntilRefreshed()
    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    assertNull(file.tracker)
    file.assertAffectedChangeLists("Default")
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
      file.assertAffectedChangeLists("Test", "Default")

      val range = tracker.getRanges()!![1]
      file.moveAllChangesTo("Test 2")
      file.assertAffectedChangeLists("Test 2")
      assertNotNull(file.tracker)
    }
    assertNotNull(file.tracker)

    clm.waitUntilRefreshed()
    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    assertNull(file.tracker)
    file.assertAffectedChangeLists("Test 2")
  }

  @Bombed(year = 2018, month = Calendar.JUNE, day = 1, user = "Aleksey.Pivovarov")
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
      file.assertAffectedChangeLists("Test", "Default")

      val range = tracker.getRanges()!![1]
      tracker.moveToChangelist(range, "Default".asListNameToList())
      file.assertAffectedChangeLists("Default")

      releaseUnneededTrackers()
      assertNull(file.tracker)
    }
    assertNull(file.tracker)

    clm.waitUntilRefreshed()
    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    assertNull(file.tracker)
    file.assertAffectedChangeLists("Default")
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
      file.assertAffectedChangeLists("Test", "Default")

      val range = tracker.getRanges()!![1]
      file.moveAllChangesTo("Test 2")
      file.assertAffectedChangeLists("Test 2")

      releaseUnneededTrackers()
      assertNull(file.tracker)
    }
    assertNull(file.tracker)

    clm.waitUntilRefreshed()
    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    assertNull(file.tracker)
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
      file.assertAffectedChangeLists("Test", "Default")

      val range = tracker.getRanges()!![0]
      tracker.moveToChangelist(range, "Test".asListNameToList())
      file.assertAffectedChangeLists("Test")
    }
    assertNotNull(file.tracker)

    clm.waitUntilRefreshed()
    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    assertNull(file.tracker)
    file.assertAffectedChangeLists("Test")
  }

  @Bombed(year = 2018, month = Calendar.JUNE, day = 1, user = "Aleksey.Pivovarov")
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
        tracker.moveToChangelist(range, "Default".asListNameToList())
        file.assertAffectedChangeLists("Default")
      }
    }
    assertNull(file.tracker)

    clm.waitUntilRefreshed()
    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    assertNull(file.tracker)
    file.assertAffectedChangeLists("Default")
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

  @Bombed(year = 2018, month = Calendar.JUNE, day = 1, user = "Aleksey.Pivovarov")
  fun `test file rename - with partial changes`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e2")
    refreshCLM()
    file.moveAllChangesTo("Test")
    runCommand { file.document.replaceString(0, 1, "a2") }
    FILE_1.toFilePath.assertAffectedChangeLists("Test", "Default")

    runWriteAction {
      file.rename(this, FILE_2)
    }
    setBaseVersion(FILE_2, "a_b_c_d_e2", FILE_1)
    refreshCLM()
    FILE_2.toFilePath.assertAffectedChangeLists("Test", "Default")
  }

  @Bombed(year = 2018, month = Calendar.JUNE, day = 1, user = "Aleksey.Pivovarov")
  fun `test file rename - with partial changes, try release tracker during CLM refresh`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e2")
    refreshCLM()
    file.moveAllChangesTo("Test")
    runCommand { file.document.replaceString(0, 1, "a2") }
    FILE_1.toFilePath.assertAffectedChangeLists("Test", "Default")

    runWriteAction {
      file.rename(this, FILE_2)
    }
    setBaseVersion(FILE_2, "a_b_c_d_e2", FILE_1)

    changeProvider.awaitAndBlockRefresh().use {
      file.withOpenedEditor {
        lstm.waitUntilBaseContentsLoaded()
        releaseUnneededTrackers()

        FILE_1.toFilePath.assertAffectedChangeLists("Test", "Default")
      }
    }

    clm.waitUntilRefreshed()
    lstm.waitUntilBaseContentsLoaded()
    releaseUnneededTrackers()

    FILE_2.toFilePath.assertAffectedChangeLists("Test", "Default")
  }
}