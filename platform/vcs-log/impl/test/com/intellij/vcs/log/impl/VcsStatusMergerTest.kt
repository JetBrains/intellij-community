// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl

import com.intellij.openapi.vcs.changes.Change
import org.junit.Test
import kotlin.test.assertEquals

class VcsStatusMergerTest {

  @Test
  fun simpleMergeTest() {
    val statuses = listOf(listOf(modified("file1"),
                                 modified("file2"),
                                 modified("file3")),
                          listOf(modified("file1"),
                                 modified("file2"),
                                 modified("file4")))
    val mergedStatusInfo = VcsFileStatusInfoMerger().merge(statuses)
    assertEquals(listOf(modified("file1"),
                        modified("file2")), mergedStatusInfo.map { it.statusInfo })
  }

  @Test
  fun renamedModifiedTest() {
    val statuses = listOf(listOf(renamed("before", "after")),
                          listOf(modified("after")))
    val mergedStatusInfo = VcsFileStatusInfoMerger().merge(statuses)
    assertEquals(listOf(), mergedStatusInfo.map { it.statusInfo })
  }

  @Test
  fun addedModifiedTest() {
    val statuses = listOf(listOf(added("file1")),
                          listOf(modified("file1")))
    val mergedStatusInfo = VcsFileStatusInfoMerger().merge(statuses)
    assertEquals(listOf(modified("file1")), mergedStatusInfo.map { it.statusInfo })
  }

  @Test
  fun deletedRenamedTest() {
    val statuses = listOf(listOf(renamed("before", "after")),
                          listOf(deleted("before")))
    val mergedStatusInfo = VcsFileStatusInfoMerger().merge(statuses)
    assertEquals(listOf(deleted("before")), mergedStatusInfo.map { it.statusInfo })
  }

  private fun modified(path: String) = VcsFileStatusInfo(Change.Type.MODIFICATION, path, null)
  private fun added(path: String) = VcsFileStatusInfo(Change.Type.NEW, path, null)
  private fun deleted(path: String) = VcsFileStatusInfo(Change.Type.DELETED, path, null)
  private fun renamed(beforePath: String, afterPath: String) = VcsFileStatusInfo(Change.Type.MOVED, afterPath, beforePath)
}