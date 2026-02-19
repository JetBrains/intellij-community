// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.vcs.BasePartiallyExcludedChangesTest
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.RangeExclusionState
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.impl.shared.changes.PartialChangesHolder
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.runBlocking

internal class PartialChangesHolderTest: BasePartiallyExcludedChangesTest() {
  private val FILE_1 = "file1.txt"

  fun `test holder state is updated`() {
    setHolderPaths(FILE_1)
    assertIncluded()

    val file = addLocalFile(name = FILE_1, content = "a_b_c_d_e", baseContent = "a_b1_c_d1_e")
    refreshCLM()

    file.withOpenedEditor {
      val tracker = file.tracker as PartialLocalLineStatusTracker
      lstm.waitUntilBaseContentsLoaded()
      waitExclusionStateUpdate()

      assertPartialChangesHolderState(file, listOf(RangeExclusionState.Excluded, RangeExclusionState.Excluded))

      tracker.exclude(0, false)
      assertPartialChangesHolderState(file, listOf(RangeExclusionState.Included, RangeExclusionState.Excluded))

      tracker.exclude(1, false)
      assertPartialChangesHolderState(file, listOf(RangeExclusionState.Included, RangeExclusionState.Included))
    }
  }

  fun `test holder updated on tracker release`() {
    setHolderPaths(FILE_1)
    assertIncluded()

    val file = addLocalFile(name = FILE_1, content = "a_b_c_d_e", baseContent = "a_b1_c_d1_e")
    refreshCLM()

    file.withOpenedEditor {
      lstm.waitUntilBaseContentsLoaded()
      waitExclusionStateUpdate()
      assertPartialChangesHolderState(file, listOf(RangeExclusionState.Excluded, RangeExclusionState.Excluded))
    }

    assertPartialChangesHolderState(file, null)
  }


  private fun assertPartialChangesHolderState(file: VirtualFile, expected: List<RangeExclusionState>?) {
    runBlocking {
      waitUntilAssertSucceeds {
        val rangesInHolder = PartialChangesHolder.getInstance(project).getRanges(VcsUtil.getFilePath(file))
        assertEquals(expected, rangesInHolder?.map { it.exclusionState })
      }
    }
  }
}