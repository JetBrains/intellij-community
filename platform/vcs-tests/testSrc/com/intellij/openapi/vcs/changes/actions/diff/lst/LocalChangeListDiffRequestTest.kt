// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff.lst

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.vcs.BaseLineStatusTrackerManagerTest
import com.intellij.openapi.vcs.LineStatusTrackerTestUtil.parseInput
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vfs.VirtualFile

class LocalChangeListDiffRequestTest : BaseLineStatusTrackerManagerTest() {
  private val FILE_1 = "file1.txt"

  fun `test partial tracker lifecycle - the tracker exists after the assignment`() {
    createChangelist("Test")
    val file = addLocalFile(name = FILE_1, content = "a_b_c_d_e", baseContent = "a_b1_c_d1_e")
    refreshCLM()
    file.assertNullTracker()

    val request = LocalChangeListDiffRequest(project, file, file.document, DEFAULT.asListNameToId(), DEFAULT, createContentRequest(file))

    request.onAssigned(true)
    try {
      // The diff tool selection reads the tracker immediately after the assignment.
      val tracker = request.lineStatusTracker
      assertTrue(tracker is PartialLocalLineStatusTracker)
      assertTrue(tracker!!.isOperational())
    }
    finally {
      request.onAssigned(false)
    }
    file.assertNullTracker()
  }

  @Suppress("DEPRECATION")
  fun `test the deprecated constructor takes the local document`() {
    val file = addLocalFile(name = FILE_1, content = "a_b_c_d_e", baseContent = "a_b1_c_d1_e")

    val request = LocalChangeListDiffRequest(project, file, DEFAULT.asListNameToId(), DEFAULT, createContentRequest(file))

    assertSame(file.document, request.document)
  }

  private fun createContentRequest(file: VirtualFile): SimpleDiffRequest {
    val contentFactory = DiffContentFactory.getInstance()
    val baseContent = contentFactory.create(project, parseInput("a_b1_c_d1_e"))
    val localContent = contentFactory.create(project, file)
    return SimpleDiffRequest(null, baseContent, localContent, null, null)
  }
}
