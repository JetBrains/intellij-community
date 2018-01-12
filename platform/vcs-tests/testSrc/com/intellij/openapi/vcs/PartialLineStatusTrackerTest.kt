/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.vcs

import com.intellij.diff.util.Side
import com.intellij.openapi.vcs.ex.Range

class PartialLineStatusTrackerTest : BaseLineStatusTrackerTestCase() {
  fun testSimple1() {
    testPartial("1234_2345_3456") {
      "12".insertAfter("a")

      range().assertChangelist("Default")
      assertAffectedChangelists("Default")
    }
  }

  fun testSimple2() {
    testPartial("1234_2345_3456") {
      "12".insertAfter("a")

      range().assertChangelist("Default")

      setDefaultChangelist("Test")
      "12".insertBefore("X_Y_Z")

      range().assertChangelist("Default")
      assertAffectedChangelists("Default")

      "3456".replace("X_Y_Z")

      range(0).assertChangelist("Default")
      range(1).assertChangelist("Test")
      assertAffectedChangelists("Default", "Test")
    }
  }

  fun testRangeMerging1() {
    testPartial("1234_2345_3456") {
      "12".insertAfter("a")

      range().assertChangelist("Default")
      assertAffectedChangelists("Default")

      setDefaultChangelist("Test")

      range().assertChangelist("Default")
      assertAffectedChangelists("Default")

      "56".insertAfter("b")

      range(0).assertChangelist("Default")
      range(1).assertChangelist("Test")
      assertAffectedChangelists("Default", "Test")

      "2345".insertAfter("c")

      range().assertChangelist("Test")
      assertAffectedChangelists("Test")
    }
  }

  fun testRangeMerging2() {
    testPartial("1234_2345_3456") {
      "12".insertAfter("a")

      range().assertChangelist("Default")
      assertAffectedChangelists("Default")

      setDefaultChangelist("Test")

      range().assertChangelist("Default")
      assertAffectedChangelists("Default")

      "56".insertAfter("b")

      range(0).assertChangelist("Default")
      range(1).assertChangelist("Test")
      assertAffectedChangelists("Default", "Test")

      "2345_".delete()

      range().assertChangelist("Test")
      assertAffectedChangelists("Test")
    }
  }

  fun testMovements1() {
    testPartial("1234_2345_3456") {
      "12".insertAfter("a")

      range().assertChangelist("Default")
      assertAffectedChangelists("Default")

      createChangelist("Test")
      range().moveTo("Test")

      range().assertChangelist("Test")
      assertAffectedChangelists("Test")
    }
  }

  fun testMovements2() {
    testPartial("1234_2345_3456") {
      "12".insertAfter("a")

      range().assertChangelist("Default")
      assertAffectedChangelists("Default")

      setDefaultChangelist("Test")

      range().assertChangelist("Default")
      assertAffectedChangelists("Default")

      "56".insertAfter("b")

      range(0).assertChangelist("Default")
      range(1).assertChangelist("Test")
      assertAffectedChangelists("Default", "Test")

      range(0).moveTo("Test")

      range(0).assertChangelist("Test")
      range(1).assertChangelist("Test")
      assertAffectedChangelists("Test")

      range(1).moveTo("Default")

      range(0).assertChangelist("Test")
      range(1).assertChangelist("Default")
      assertAffectedChangelists("Default", "Test")
    }
  }

  fun testMovements3() {
    testPartial("1234_2345_3456") {
      "12".insertAfter("a")

      range().assertChangelist("Default")
      assertAffectedChangelists("Default")

      setDefaultChangelist("Test")

      range().assertChangelist("Default")
      assertAffectedChangelists("Default")

      "56".insertAfter("b")

      range(0).assertChangelist("Default")
      range(1).assertChangelist("Test")
      assertAffectedChangelists("Default", "Test")

      removeChangelist("Default")

      range(0).assertChangelist("Test")
      range(1).assertChangelist("Test")
      assertAffectedChangelists("Test")
    }
  }

  fun testWhitespaceBlockMerging() {
    test("A_B_C_D_E_") {
      "A".replace("C_D_E")
      (2 th "C_D_E_").delete()
      assertTextContentIs("C_D_E_B_")
      assertRanges(Range(0, 3, 0, 1), Range(4, 4, 2, 5))
    }

    test("A_ _C_D_E_") {
      "A".replace("C_D_E")
      (2 th "C_D_E_").delete()
      assertTextContentIs("C_D_E_ _")
      assertRanges(Range(0, 0, 0, 2), Range(3, 4, 5, 5))
    }

    testPartial("A_ _C_D_E_") {
      "A".replace("C_D_E")
      (2 th "C_D_E_").delete()
      assertTextContentIs("C_D_E_ _")
      assertRanges(Range(0, 0, 0, 2), Range(3, 4, 5, 5))
    }

    testPartial("A_ _C_D_E_") {
      "A".replace("C_D_E")

      setDefaultChangelist("Test")

      (2 th "C_D_E_").delete()
      assertTextContentIs("C_D_E_ _")
      assertRanges(Range(0, 3, 0, 1), Range(4, 4, 2, 5))
    }
  }

  fun testPartialCommit1() {
    testPartial("A_B_C_D_E_F_G_H_") {
      "B".replace("B1")
      "D_".delete()
      "F_".insertAfter("M_")
      "G_".insertAfter("N_")
      range(1).moveTo("Test")
      range(3).moveTo("Test")

      assertTextContentIs("A_B1_C_E_F_M_G_N_H_")
      assertBaseTextContentIs("A_B_C_D_E_F_G_H_")
      assertAffectedChangelists("Default", "Test")

      val helper = partialTracker.handlePartialCommit(Side.LEFT, "Test")
      helper.applyChanges()

      assertHelperContentIs("A_B_C_E_F_G_N_H_", helper)
      assertTextContentIs("A_B1_C_E_F_M_G_N_H_")
      assertBaseTextContentIs("A_B_C_E_F_G_N_H_")
      assertAffectedChangelists("Default")
    }
  }

  fun testPartialCommit2() {
    testPartial("A_B_C_D_E_F_G_H_") {
      "B".replace("B1")
      "D_".delete()
      "F_".insertAfter("M_")
      "G_".insertAfter("N_")
      range(1).moveTo("Test")
      range(3).moveTo("Test")

      assertTextContentIs("A_B1_C_E_F_M_G_N_H_")
      assertBaseTextContentIs("A_B_C_D_E_F_G_H_")
      assertAffectedChangelists("Default", "Test")

      val helper = partialTracker.handlePartialCommit(Side.LEFT, "Default")
      helper.applyChanges()

      assertHelperContentIs("A_B1_C_D_E_F_M_G_H_", helper)
      assertTextContentIs("A_B1_C_E_F_M_G_N_H_")
      assertBaseTextContentIs("A_B1_C_D_E_F_M_G_H_")
      assertAffectedChangelists("Test")
    }
  }

  fun testPartialCommit3() {
    testPartial("A_B_C_D_E_F_G_H_") {
      "B".replace("B1")
      "D_".delete()
      "F_".insertAfter("M_")
      "G_".insertAfter("N_")
      range(1).moveTo("Test")
      range(3).moveTo("Test")

      assertTextContentIs("A_B1_C_E_F_M_G_N_H_")
      assertBaseTextContentIs("A_B_C_D_E_F_G_H_")
      assertAffectedChangelists("Default", "Test")

      val helper = partialTracker.handlePartialCommit(Side.RIGHT, "Test")
      helper.applyChanges()

      assertHelperContentIs("A_B1_C_D_E_F_M_G_H_", helper)
      assertTextContentIs("A_B1_C_D_E_F_M_G_H_")
      assertBaseTextContentIs("A_B_C_D_E_F_G_H_")
      assertAffectedChangelists("Default")
    }
  }

  fun testPartialCommit4() {
    testPartial("A_B_C_D_E_F_G_H_") {
      "B".replace("B1")
      "D_".delete()
      "F_".insertAfter("M_")
      "G_".insertAfter("N_")
      range(1).moveTo("Test")
      range(3).moveTo("Test")

      assertTextContentIs("A_B1_C_E_F_M_G_N_H_")
      assertBaseTextContentIs("A_B_C_D_E_F_G_H_")
      assertAffectedChangelists("Default", "Test")

      tracker.doFrozen(Runnable {
        runCommand {
          "B1_".replace("X_Y_Z_")

          val helper = partialTracker.handlePartialCommit(Side.LEFT, "Default")
          helper.applyChanges()

          assertHelperContentIs("A_X_Y_Z_C_D_E_F_M_G_H_", helper)
          assertTextContentIs("A_X_Y_Z_C_E_F_M_G_N_H_")
          assertBaseTextContentIs("A_X_Y_Z_C_D_E_F_M_G_H_")
          assertAffectedChangelists("Test")
        }
      })
    }
  }

  fun testPartialCommit5() {
    testPartial("A_B_C_D_E_F_G_H_") {
      "B".replace("B1")
      "D_".delete()
      "F_".insertAfter("M_")
      "G_".insertAfter("N_")
      range(1).moveTo("Test")
      range(3).moveTo("Test")

      assertTextContentIs("A_B1_C_E_F_M_G_N_H_")
      assertBaseTextContentIs("A_B_C_D_E_F_G_H_")
      assertAffectedChangelists("Default", "Test")

      tracker.doFrozen(Runnable {
        runCommand {
          "B1_".replace("X_Y_Z_")

          val helper = partialTracker.handlePartialCommit(Side.LEFT, "Default")

          "N".replace("N2")
          "M".replace("M2")

          vcsDocument.setReadOnly(false)
          vcsDocument.replaceString(0, 10, "XXXXX_IGNORED")
          vcsDocument.setReadOnly(true)

          helper.applyChanges()

          assertHelperContentIs("A_X_Y_Z_C_D_E_F_M_G_H_", helper)
          assertTextContentIs("A_X_Y_Z_C_E_F_M2_G_N2_H_")
          assertBaseTextContentIs("A_X_Y_Z_C_D_E_F_M_G_H_")
          assertAffectedChangelists("Test")
        }
      })
    }
  }

}
