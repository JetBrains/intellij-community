// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.openapi.vcs.LineStatusTrackerTestUtil.parseInput
import com.intellij.openapi.vcs.ex.ExclusionState.*
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker

class PartiallyExcludedChangesTest : BasePartiallyExcludedChangesTest() {
  private val FILE_1 = "file1.txt"
  private val FILE_2 = "file2.txt"

  fun `test operations without trackers`() {
    setHolderPaths("file1", "file2", "file3", "file4", "file5")
    assertIncluded()

    toggle("file1")
    assertIncluded("file1")

    toggle("file1", "file3")
    assertIncluded("file1", "file3")

    toggle("file1", "file3", "file5")
    assertIncluded("file1", "file3", "file5")

    toggle("file1", "file5")
    assertIncluded("file3")

    toggle("file3")
    assertIncluded()

    include("file3")
    assertIncluded("file3")

    include("file3", "file5")
    assertIncluded("file3", "file5")

    exclude("file5")
    assertIncluded("file3")

    "file3".assertExcludedState(ALL_INCLUDED)
    "file4".assertExcludedState(ALL_EXCLUDED)
  }

  fun `test operations with fully tracked file`() {
    setHolderPaths(FILE_1, "file1", "file2", "file3")
    assertIncluded()

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b1_c_d1_e")
    refreshCLM()

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker
      lstm.waitUntilBaseContentsLoaded()
      waitExclusionStateUpdate()
      assertIncluded()

      toggle(FILE_1, "file1")
      assertIncluded(FILE_1, "file1")
      FILE_1.assertExcludedState(ALL_INCLUDED)

      exclude(FILE_1)
      assertIncluded("file1")
      FILE_1.assertExcludedState(ALL_EXCLUDED)

      include(FILE_1, "file2")
      assertIncluded(FILE_1, "file1", "file2")

      tracker.setExcludedFromCommit(true)
      assertIncluded(FILE_1, "file1", "file2")
      waitExclusionStateUpdate()
      assertIncluded("file1", "file2")

      tracker.setExcludedFromCommit(false)
      assertIncluded("file1", "file2")
      waitExclusionStateUpdate()
      assertIncluded(FILE_1, "file1", "file2")
    }
  }

  fun `test operations with partially tracked file`() {
    setHolderPaths(FILE_1, "file1", "file2", "file3")
    assertIncluded()

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b1_c_d1_e")
    refreshCLM()

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker
      lstm.waitUntilBaseContentsLoaded()
      waitExclusionStateUpdate()
      assertIncluded()

      tracker.exclude(0, false)
      assertIncluded(FILE_1)
      FILE_1.assertExcludedState(PARTIALLY)

      toggle("file1")
      assertIncluded(FILE_1, "file1")

      toggle(FILE_1)
      assertIncluded(FILE_1, "file1")
      FILE_1.assertExcludedState(ALL_INCLUDED)

      tracker.exclude(0, true)
      assertIncluded(FILE_1, "file1")
      FILE_1.assertExcludedState(PARTIALLY)

      toggle(FILE_1, "file1")
      assertIncluded(FILE_1, "file1")
      FILE_1.assertExcludedState(ALL_INCLUDED)

      tracker.exclude(0, true)
      assertIncluded(FILE_1, "file1")
      FILE_1.assertExcludedState(PARTIALLY)

      toggle(FILE_1, "file3")
      assertIncluded(FILE_1, "file1", "file3")
      FILE_1.assertExcludedState(ALL_INCLUDED)
    }
  }

  fun `test operations with unchanged tracked file`() {
    setHolderPaths(FILE_1, "file1", "file2", "file3")
    assertIncluded()

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e")
    refreshCLM()

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker
      lstm.waitUntilBaseContentsLoaded()
      waitExclusionStateUpdate()
      assertIncluded()
      FILE_1.assertExcludedState(ALL_EXCLUDED, NO_CHANGES)

      toggle("file1")
      assertIncluded("file1")

      toggle(FILE_1)
      assertIncluded(FILE_1, "file1")
      FILE_1.assertExcludedState(ALL_INCLUDED, NO_CHANGES)

      toggle(FILE_1, "file1")
      assertIncluded()
      FILE_1.assertExcludedState(ALL_EXCLUDED, NO_CHANGES)

      toggle(FILE_1, "file1")
      assertIncluded(FILE_1, "file1")
      FILE_1.assertExcludedState(ALL_INCLUDED, NO_CHANGES)

      tracker.setExcludedFromCommit(true)
      waitExclusionStateUpdate()
      FILE_1.assertExcludedState(ALL_INCLUDED, NO_CHANGES)
    }
  }

  fun `test state transition with trackers`() {
    setHolderPaths(FILE_1, FILE_2, "file1", "file2", "file3")
    assertIncluded()

    val file1 = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a1_b_c_d_e1")
    val file2 = addLocalFile(FILE_2, "a_b_c_d_e")
    setBaseVersion(FILE_2, "a2_b_c_d_e2")
    refreshCLM()

    include(FILE_1, "file1")
    assertIncluded(FILE_1, "file1")

    file1.withOpenedEditor {
      file2.withOpenedEditor {
        assertIncluded(FILE_1, "file1")

        lstm.waitUntilBaseContentsLoaded()
        waitExclusionStateUpdate()

        assertIncluded(FILE_1, "file1")
        FILE_1.assertExcludedState(ALL_INCLUDED)
        FILE_2.assertExcludedState(ALL_EXCLUDED)
      }
    }

    assertIncluded(FILE_1, "file1")

    releaseUnneededTrackers()
    waitExclusionStateUpdate()

    assertIncluded(FILE_1, "file1")
    assertNull(file1.tracker)
    assertNull(file2.tracker)
  }

  fun `test state transition with empty trackers`() {
    setHolderPaths(FILE_1, FILE_2, "file1", "file2", "file3")
    assertIncluded()

    val file1 = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b_c_d_e")
    val file2 = addLocalFile(FILE_2, "a_b_c_d_e")
    setBaseVersion(FILE_2, "a_b_c_d_e")
    refreshCLM()

    include(FILE_1, "file1")
    assertIncluded(FILE_1, "file1")

    file1.withOpenedEditor {
      file2.withOpenedEditor {
        assertIncluded(FILE_1, "file1")

        lstm.waitUntilBaseContentsLoaded()
        waitExclusionStateUpdate()

        assertIncluded(FILE_1, "file1")
        FILE_1.assertExcludedState(ALL_INCLUDED, NO_CHANGES)
        FILE_2.assertExcludedState(ALL_EXCLUDED, NO_CHANGES)
      }
    }

    assertIncluded(FILE_1, "file1")

    releaseUnneededTrackers()
    waitExclusionStateUpdate()

    assertIncluded(FILE_1, "file1")
    assertNull(file1.tracker)
    assertNull(file2.tracker)
  }

  fun `test state transition with trackers - partially excluded changes`() {
    setHolderPaths(FILE_1, FILE_2, "file1", "file2", "file3")
    assertIncluded()

    val file1 = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a1_b_c_d_e1")
    val file2 = addLocalFile(FILE_2, "a_b_c_d_e")
    setBaseVersion(FILE_2, "a2_b_c_d_e2")
    refreshCLM()

    include(FILE_1, "file1")
    assertIncluded(FILE_1, "file1")

    file1.withOpenedEditor {
      file2.withOpenedEditor {
        val tracker1 = file1.tracker as PartialLocalLineStatusTracker
        val tracker2 = file2.tracker as PartialLocalLineStatusTracker

        assertIncluded(FILE_1, "file1")

        lstm.waitUntilBaseContentsLoaded()
        waitExclusionStateUpdate()

        assertIncluded(FILE_1, "file1")
        FILE_1.assertExcludedState(ALL_INCLUDED)
        FILE_2.assertExcludedState(ALL_EXCLUDED)

        tracker1.exclude(0, true)
        tracker2.exclude(0, false)

        assertIncluded(FILE_1, FILE_2, "file1")
        FILE_1.assertExcludedState(PARTIALLY)
        FILE_2.assertExcludedState(PARTIALLY)

        tracker1.exclude(0, false)
        assertIncluded(FILE_1, FILE_2, "file1")
        FILE_1.assertExcludedState(ALL_INCLUDED)
        FILE_2.assertExcludedState(PARTIALLY)
      }
    }
    assertIncluded(FILE_1, FILE_2, "file1")

    releaseUnneededTrackers()
    waitExclusionStateUpdate()

    assertNull(file1.tracker)
    assertNotNull(file2.tracker)
    assertIncluded(FILE_1, FILE_2, "file1")
    FILE_1.assertExcludedState(ALL_INCLUDED)
    FILE_2.assertExcludedState(PARTIALLY)

    lstm.resetExcludedFromCommitMarkers()

    assertNull(file1.tracker)
    assertNull(file2.tracker)
    assertIncluded(FILE_1, FILE_2, "file1")
    FILE_1.assertExcludedState(ALL_INCLUDED)
    FILE_2.assertExcludedState(ALL_INCLUDED)
  }

  fun `test state transition with trackers - changelists`() {
    createChangelist("Test")

    setHolderPaths(FILE_1, FILE_2, "file1", "file2", "file3")
    assertIncluded()

    val file1 = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a1_b_c1_d_e1")
    val file2 = addLocalFile(FILE_2, "a_b_c_d_e")
    setBaseVersion(FILE_2, "a2_b_c2_d_e2")
    refreshCLM()

    include(FILE_1, "file1")
    assertIncluded(FILE_1, "file1")

    file1.withOpenedEditor {
      file2.withOpenedEditor {
        val tracker1 = file1.tracker as PartialLocalLineStatusTracker
        val tracker2 = file2.tracker as PartialLocalLineStatusTracker
        lstm.waitUntilBaseContentsLoaded()

        tracker1.moveTo(2, "Test")
        tracker2.moveTo(2, "Test")
        waitExclusionStateUpdate()

        assertIncluded(FILE_1, "file1")
        FILE_1.assertExcludedState(ALL_INCLUDED)
        FILE_2.assertExcludedState(ALL_EXCLUDED)

        tracker1.exclude(2, true)
        tracker2.exclude(2, false)

        assertIncluded(FILE_1, "file1")
        FILE_1.assertExcludedState(ALL_INCLUDED)
        FILE_2.assertExcludedState(ALL_EXCLUDED)

        tracker1.moveTo(2, DEFAULT)
        tracker2.moveTo(2, DEFAULT)
        waitExclusionStateUpdate()

        assertIncluded(FILE_1, FILE_2, "file1")
        FILE_1.assertExcludedState(PARTIALLY)
        FILE_2.assertExcludedState(PARTIALLY)

        tracker1.moveTo(2, "Test")
        tracker2.moveTo(2, "Test")
        waitExclusionStateUpdate()

        assertIncluded(FILE_1, "file1")
        FILE_1.assertExcludedState(ALL_INCLUDED)
        FILE_2.assertExcludedState(ALL_EXCLUDED)
      }
    }
    assertIncluded(FILE_1, "file1")
  }

  fun `test state transition with trackers - initialisation`() {
    setHolderPaths(FILE_1, FILE_2, "file1", "file2", "file3")
    assertIncluded()

    val file1 = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a1_b_c1_d_e1")
    val file2 = addLocalFile(FILE_2, "a_b_c_d_e")
    setBaseVersion(FILE_2, "a2_b_c2_d_e2")
    refreshCLM()

    file1.withOpenedEditor {
      file2.withOpenedEditor {
        val tracker1 = file1.tracker as PartialLocalLineStatusTracker
        val tracker2 = file2.tracker as PartialLocalLineStatusTracker

        include(FILE_1)
        assertIncluded(FILE_1)
        FILE_1.assertExcludedState(ALL_INCLUDED, NO_CHANGES)
        FILE_2.assertExcludedState(ALL_EXCLUDED, NO_CHANGES)
        assertNull(tracker1.getRanges())
        assertNull(tracker2.getRanges())

        lstm.waitUntilBaseContentsLoaded()
        waitExclusionStateUpdate()

        assertIncluded(FILE_1)
        FILE_1.assertExcludedState(ALL_INCLUDED)
        FILE_2.assertExcludedState(ALL_EXCLUDED)
      }
    }
  }

  fun `test state tracking on file modifications`() {
    setHolderPaths(FILE_1)
    include(FILE_1)
    assertIncluded(FILE_1)

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a1_b_c1_d_e1")
    refreshCLM()

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker
      lstm.waitUntilBaseContentsLoaded()

      tracker.exclude(0, true)
      tracker.exclude(2, true)

      tracker.assertExcluded(0, true)
      tracker.assertExcluded(1, false)
      tracker.assertExcluded(2, true)

      runCommand { file.document.replaceString(0, 1, "a2") }

      tracker.assertExcluded(0, true)
      tracker.assertExcluded(1, false)
      tracker.assertExcluded(2, true)

      runCommand { file.document.replaceString(4, 5, "2") }
      assertEquals(tracker.getRanges()!!.size, 2)
      tracker.assertExcluded(0, false)
      tracker.assertExcluded(1, true)

      runCommand { file.document.replaceString(4, 5, parseInput("2_i_i_i_i")) }
      assertEquals(tracker.getRanges()!!.size, 2)
      tracker.assertExcluded(0, false)
      tracker.assertExcluded(1, true)
    }
  }

  fun `test state tracking on file modifications separated with whitespaces`() {
    setHolderPaths(FILE_1)
    include(FILE_1)
    assertIncluded(FILE_1)

    val file = addLocalFile(FILE_1, "a_ _c_ _e")
    setBaseVersion(FILE_1, "a1_ _c1_ _e1")
    refreshCLM()

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker
      lstm.waitUntilBaseContentsLoaded()

      tracker.exclude(0, true)
      tracker.exclude(2, true)

      tracker.assertExcluded(0, true)
      tracker.assertExcluded(1, false)
      tracker.assertExcluded(2, true)

      runCommand { file.document.replaceString(0, 1, "a2") }

      tracker.assertExcluded(0, true)
      tracker.assertExcluded(1, false)
      tracker.assertExcluded(2, true)

      runCommand { file.document.replaceString(4, 5, "2") }
      assertEquals(tracker.getRanges()!!.size, 2)
      tracker.assertExcluded(0, false)
      tracker.assertExcluded(1, true)

      runCommand { file.document.replaceString(4, 5, parseInput("2_i_i_i_i")) }
      assertEquals(tracker.getRanges()!!.size, 2)
      tracker.assertExcluded(0, false)
      tracker.assertExcluded(1, true)
    }
  }
}