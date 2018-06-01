// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vcs.changes.Change
import junit.framework.TestCase

class ChangeListManagerTest : BaseChangeListsTest() {
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
    TestCase.assertTrue(clm.allChanges.all { it.type == Change.Type.DELETED })

    removeBaseVersion(FILE_2)
    setBaseVersion(FILE_1, "oldText", FILE_2)
    addLocalFile(FILE_1, "text")
    refreshCLM()
    assertEquals(1, clm.allChanges.size)
    assertEquals(Change.Type.MOVED, clm.allChanges.first().type)
  }

  fun `test new changes moved to default list`() {
    createChangelist("Test")

    val file = addLocalFile(FILE_1, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b1_c_d1_e")
    refreshCLM()
    file.assertAffectedChangeLists("Default Changelist")

    setDefaultChangeList("Test")
    setBaseVersion(FILE_2, "a_b1_c_d1_e")
    refreshCLM()
    FILE_1.toFilePath.assertAffectedChangeLists("Default Changelist")
    FILE_2.toFilePath.assertAffectedChangeLists("Test")
  }

  fun `test modifications do not move files to default`() {
    createChangelist("Test")

    val file1 = addLocalFile(FILE_1, "a_b_c_d_e")
    val file2 = addLocalFile(FILE_2, "a_b_c_d_e")
    setBaseVersion(FILE_1, "a_b1_c_d1_e")
    setBaseVersion(FILE_2, null)
    refreshCLM()
    file1.assertAffectedChangeLists("Default Changelist")
    file2.assertAffectedChangeLists("Default Changelist")

    setDefaultChangeList("Test")

    runWriteAction {
      file1.document.setText("New Text")
      file2.document.setText("New Text")
    }
    refreshCLM()
    file1.assertAffectedChangeLists("Default Changelist")
    file2.assertAffectedChangeLists("Default Changelist")

    setBaseVersion(FILE_1, "a_b1_c_d1_e_f_g")
    refreshCLM()
    file1.assertAffectedChangeLists("Default Changelist")
    file2.assertAffectedChangeLists("Default Changelist")
  }

  fun `test renames do not move files to default`() {
    createChangelist("Test")

    setBaseVersion(FILE_1, "a_b1_c_d1_e")
    refreshCLM()
    FILE_1.toFilePath.assertAffectedChangeLists("Default Changelist")

    setDefaultChangeList("Test")

    val file1 = addLocalFile(FILE_1, "a_b_c_d_e")
    refreshCLM()
    FILE_1.toFilePath.assertAffectedChangeLists("Default Changelist")

    runWriteAction {
      file1.document.setText("New Text")
    }
    refreshCLM()
    FILE_1.toFilePath.assertAffectedChangeLists("Default Changelist")

    setBaseVersion(FILE_1, null)
    refreshCLM()
    FILE_1.toFilePath.assertAffectedChangeLists("Default Changelist")

    setBaseVersion(FILE_1, "a_b1_c_d1_e_f_g", FILE_2)
    refreshCLM()
    FILE_1.toFilePath.assertAffectedChangeLists("Default Changelist")

    removeLocalFile(FILE_1)
    refreshCLM()
    FILE_2.toFilePath.assertAffectedChangeLists("Default Changelist")
  }
}