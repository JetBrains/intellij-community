// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.diff.util.Side
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.vcs.ex.Range

class PartialLineStatusTrackerTest : BaseLineStatusTrackerTestCase() {
  fun testSimple1() {
    testPartial("1234_2345_3456") {
      "12".insertAfter("a")

      range().assertChangeList("Default Changelist")
      assertAffectedChangeLists("Default Changelist")
    }
  }

  fun testSimple2() {
    testPartial("1234_2345_3456") {
      "12".insertAfter("a")

      range().assertChangeList("Default Changelist")

      createChangeList_SetDefault("Test")
      "12".insertBefore("X_Y_Z")

      range().assertChangeList("Default Changelist")
      assertAffectedChangeLists("Default Changelist")

      "3456".replace("X_Y_Z")

      range(0).assertChangeList("Default Changelist")
      range(1).assertChangeList("Test")
      assertAffectedChangeLists("Default Changelist", "Test")
    }
  }

  fun testRangeMerging1() {
    testPartial("1234_2345_3456") {
      "12".insertAfter("a")

      range().assertChangeList("Default Changelist")
      assertAffectedChangeLists("Default Changelist")

      createChangeList_SetDefault("Test")

      range().assertChangeList("Default Changelist")
      assertAffectedChangeLists("Default Changelist")

      "56".insertAfter("b")

      range(0).assertChangeList("Default Changelist")
      range(1).assertChangeList("Test")
      assertAffectedChangeLists("Default Changelist", "Test")

      "2345".insertAfter("c")

      range().assertChangeList("Test")
      assertAffectedChangeLists("Test")
    }
  }

  fun testRangeMerging2() {
    testPartial("1234_2345_3456") {
      "12".insertAfter("a")

      range().assertChangeList("Default Changelist")
      assertAffectedChangeLists("Default Changelist")

      createChangeList_SetDefault("Test")

      range().assertChangeList("Default Changelist")
      assertAffectedChangeLists("Default Changelist")

      "56".insertAfter("b")

      range(0).assertChangeList("Default Changelist")
      range(1).assertChangeList("Test")
      assertAffectedChangeLists("Default Changelist", "Test")

      "2345_".delete()

      range().assertChangeList("Test")
      assertAffectedChangeLists("Test")
    }
  }

  fun testMovements1() {
    testPartial("1234_2345_3456") {
      "12".insertAfter("a")

      range().assertChangeList("Default Changelist")
      assertAffectedChangeLists("Default Changelist")

      createChangelist("Test")
      range().moveTo("Test")

      range().assertChangeList("Test")
      assertAffectedChangeLists("Test")
    }
  }

  fun testMovements2() {
    testPartial("1234_2345_3456") {
      "12".insertAfter("a")

      range().assertChangeList("Default Changelist")
      assertAffectedChangeLists("Default Changelist")

      createChangeList_SetDefault("Test")

      range().assertChangeList("Default Changelist")
      assertAffectedChangeLists("Default Changelist")

      "56".insertAfter("b")

      range(0).assertChangeList("Default Changelist")
      range(1).assertChangeList("Test")
      assertAffectedChangeLists("Default Changelist", "Test")

      range(0).moveTo("Test")

      range(0).assertChangeList("Test")
      range(1).assertChangeList("Test")
      assertAffectedChangeLists("Test")

      range(1).moveTo("Default Changelist")

      range(0).assertChangeList("Test")
      range(1).assertChangeList("Default Changelist")
      assertAffectedChangeLists("Default Changelist", "Test")
    }
  }

  fun testMovements3() {
    testPartial("1234_2345_3456") {
      "12".insertAfter("a")

      range().assertChangeList("Default Changelist")
      assertAffectedChangeLists("Default Changelist")

      createChangeList_SetDefault("Test")

      range().assertChangeList("Default Changelist")
      assertAffectedChangeLists("Default Changelist")

      "56".insertAfter("b")

      range(0).assertChangeList("Default Changelist")
      range(1).assertChangeList("Test")
      assertAffectedChangeLists("Default Changelist", "Test")

      removeChangeList("Default Changelist")

      range(0).assertChangeList("Test")
      range(1).assertChangeList("Test")
      assertAffectedChangeLists("Test")
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

      createChangeList_SetDefault("Test")

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
      assertAffectedChangeLists("Default Changelist", "Test")

      val helper = handlePartialCommit(Side.LEFT, "Test")
      helper.applyChanges()

      assertHelperContentIs("A_B_C_E_F_G_N_H_", helper)
      assertTextContentIs("A_B1_C_E_F_M_G_N_H_")
      assertBaseTextContentIs("A_B_C_E_F_G_N_H_")
      assertAffectedChangeLists("Default Changelist")
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
      assertAffectedChangeLists("Default Changelist", "Test")

      val helper = handlePartialCommit(Side.LEFT, "Default Changelist")
      helper.applyChanges()

      assertHelperContentIs("A_B1_C_D_E_F_M_G_H_", helper)
      assertTextContentIs("A_B1_C_E_F_M_G_N_H_")
      assertBaseTextContentIs("A_B1_C_D_E_F_M_G_H_")
      assertAffectedChangeLists("Test")
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
      assertAffectedChangeLists("Default Changelist", "Test")

      val helper = handlePartialCommit(Side.RIGHT, "Test")
      helper.applyChanges()

      assertHelperContentIs("A_B1_C_D_E_F_M_G_H_", helper)
      assertTextContentIs("A_B1_C_D_E_F_M_G_H_")
      assertBaseTextContentIs("A_B_C_D_E_F_G_H_")
      assertAffectedChangeLists("Default Changelist")
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
      assertAffectedChangeLists("Default Changelist", "Test")

      tracker.doFrozen(Runnable {
        runCommandVerify {
          "B1_".replace("X_Y_Z_")

          val helper = handlePartialCommit(Side.LEFT, "Default Changelist")
          helper.applyChanges()

          assertHelperContentIs("A_X_Y_Z_C_D_E_F_M_G_H_", helper)
          assertTextContentIs("A_X_Y_Z_C_E_F_M_G_N_H_")
          assertBaseTextContentIs("A_X_Y_Z_C_D_E_F_M_G_H_")
          assertAffectedChangeLists("Test")
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
      assertAffectedChangeLists("Default Changelist", "Test")

      tracker.doFrozen(Runnable {
        runCommandVerify {
          "B1_".replace("X_Y_Z_")

          val helper = handlePartialCommit(Side.LEFT, "Default Changelist")

          "N".replace("N2")
          "M".replace("M2")

          vcsDocument.setReadOnly(false)
          vcsDocument.replaceString(0, 10, "XXXXX_IGNORED")
          vcsDocument.setReadOnly(true)

          helper.applyChanges()

          assertHelperContentIs("A_X_Y_Z_C_D_E_F_M_G_H_", helper)
          assertTextContentIs("A_X_Y_Z_C_E_F_M2_G_N2_H_")
          assertBaseTextContentIs("A_X_Y_Z_C_D_E_F_M_G_H_")
          assertAffectedChangeLists("Test")
        }
      })
    }
  }

  fun testUndo() {
    testPartial("A_B_C_D_E") {
      "B".replace("B1")
      "D_".delete()
      range(0).moveTo("Test 1")
      range(1).moveTo("Test 2")
      range(0).assertChangeList("Test 1")
      range(1).assertChangeList("Test 2")

      assertTextContentIs("A_B1_C_E")
      assertBaseTextContentIs("A_B_C_D_E")
      assertAffectedChangeLists("Test 1", "Test 2")

      "C".replace("C2")
      assertRanges(Range(1, 3, 1, 4))
      assertAffectedChangeLists("Default Changelist")

      undo()

      assertTextContentIs("A_B1_C_E")
      range(0).assertChangeList("Test 1")
      range(1).assertChangeList("Test 2")
      assertAffectedChangeLists("Test 1", "Test 2")

      redo()

      assertRanges(Range(1, 3, 1, 4))
      assertAffectedChangeLists("Default Changelist")

      undo()

      range(0).assertChangeList("Test 1")
      range(1).assertChangeList("Test 2")
      assertAffectedChangeLists("Test 1", "Test 2")
    }
  }

  fun testUndoAfterExplicitMove() {
    testPartial("A_B_C_D_E") {
      "B".replace("B1")
      "D_".delete()
      range(0).moveTo("Test 1")
      range(1).moveTo("Test 2")
      range(0).assertChangeList("Test 1")
      range(1).assertChangeList("Test 2")

      assertTextContentIs("A_B1_C_E")
      assertBaseTextContentIs("A_B_C_D_E")
      assertAffectedChangeLists("Test 1", "Test 2")

      "C".replace("C2")
      assertRanges(Range(1, 3, 1, 4))
      assertAffectedChangeLists("Default Changelist")

      range(0).moveTo("Test 1")
      assertAffectedChangeLists("Test 1")

      undo()

      assertTextContentIs("A_B1_C_E")
      range(0).assertChangeList("Test 1")
      range(1).assertChangeList("Test 1")
      assertAffectedChangeLists("Test 1")

      redo()

      assertRanges(Range(1, 3, 1, 4))
      assertAffectedChangeLists("Test 1")

      undo()

      range(0).assertChangeList("Test 1")
      range(1).assertChangeList("Test 1")
      assertAffectedChangeLists("Test 1")
    }

    testPartial("A_B_C_D_E") {
      "B".replace("B1")
      "D_".delete()
      range(0).moveTo("Test 1")
      range(1).moveTo("Test 2")
      range(0).assertChangeList("Test 1")
      range(1).assertChangeList("Test 2")

      assertTextContentIs("A_B1_C_E")
      assertBaseTextContentIs("A_B_C_D_E")
      assertAffectedChangeLists("Test 1", "Test 2")

      "C".replace("C2")
      assertRanges(Range(1, 3, 1, 4))
      assertAffectedChangeLists("Default Changelist")

      tracker.virtualFile.moveChanges("Default Changelist", "Test 1")
      assertAffectedChangeLists("Test 1")

      undo()

      assertTextContentIs("A_B1_C_E")
      range(0).assertChangeList("Test 1")
      range(1).assertChangeList("Test 1")
      assertAffectedChangeLists("Test 1")

      redo()

      assertRanges(Range(1, 3, 1, 4))
      assertAffectedChangeLists("Test 1")

      undo()

      range(0).assertChangeList("Test 1")
      range(1).assertChangeList("Test 1")
      assertAffectedChangeLists("Test 1")
    }
  }

  fun testUndoTransparentAction1() {
    testPartial("A_B_C_D_E") {
      val anotherFile = addLocalFile("Another.txt", parseInput("X_Y_Z"))
      val anotherDocument = anotherFile.document

      "C".replace("C1")
      range(0).moveTo("Test 1")

      "E".delete()

      runWriteAction {
        CommandProcessor.getInstance().executeCommand(getProject(), Runnable {
          anotherDocument.deleteString(0, 1)
        }, null, null)
      }

      runWriteAction {
        CommandProcessor.getInstance().runUndoTransparentAction {
          document.replaceString(0, 1, "A2")
        }
      }

      runWriteAction {
        CommandProcessor.getInstance().executeCommand(getProject(), Runnable {
          anotherDocument.insertString(0, "B22")
        }, null, null)
      }

      undo()

      assertTextContentIs("A_B_C1_D_E")
      range().assertChangeList("Test 1")
    }
  }

  fun testUndoTransparentAction2() {
    testPartial("A_B_C_D_E") {
      val anotherFile = addLocalFile("Another.txt", parseInput("X_Y_Z"))
      val anotherDocument = anotherFile.document

      "C".replace("C1")
      range(0).moveTo("Test 1")

      "E".delete()

      runWriteAction {
        CommandProcessor.getInstance().executeCommand(getProject(), Runnable {
          anotherDocument.deleteString(0, 1)
        }, null, null)
      }

      runWriteAction {
        CommandProcessor.getInstance().runUndoTransparentAction {
          document.replaceString(0, 1, "A2")
        }
      }

      runWriteAction {
        CommandProcessor.getInstance().executeCommand(getProject(), Runnable {
          anotherDocument.insertString(0, "B22")
        }, null, null)
      }

      runWriteAction {
        CommandProcessor.getInstance().executeCommand(getProject(), Runnable {
          anotherDocument.insertString(0, "B22")
        }, null, null)
      }

      undo()

      assertTextContentIs("A_B_C1_D_E")
      range().assertChangeList("Test 1")
    }
  }


  fun testUndoTransparentAction3() {
    testPartial("A_B_C_D_E") {
      val anotherFile = addLocalFile("Another.txt", parseInput("X_Y_Z"))
      val anotherDocument = anotherFile.document

      "C".replace("C1")
      range(0).moveTo("Test 1")

      "E".delete()

      runWriteAction {
        CommandProcessor.getInstance().executeCommand(getProject(), Runnable {
          anotherDocument.deleteString(0, 1)
        }, null, null)
      }

      runWriteAction {
        CommandProcessor.getInstance().runUndoTransparentAction {
          document.replaceString(0, 1, "A2")
        }
      }

      runWriteAction {
        CommandProcessor.getInstance().executeCommand(getProject(), Runnable {
          undoManager.nonundoableActionPerformed(DocumentReferenceManager.getInstance().create(anotherDocument), false)
        }, null, null)
      }

      undo()

      assertTextContentIs("A_B_C1_D_E")
      range().assertChangeList("Test 1")
    }
  }

}
