// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.openapi.vcs.changes.Change

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
  }
}