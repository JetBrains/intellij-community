// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data.index

import com.intellij.util.indexing.impl.KeyValueUpdateProcessor
import com.intellij.util.indexing.impl.RemovedKeyProcessor
import com.intellij.vcs.log.data.index.VcsLogPathsIndex.ChangeData
import com.intellij.vcs.log.data.index.VcsLogPathsIndex.ChangeData.MODIFIED
import com.intellij.vcs.log.data.index.VcsLogPathsIndex.ChangeData.NOT_CHANGED
import com.intellij.vcs.log.data.index.VcsLogPathsIndex.ChangeKind.RENAMED_FROM
import com.intellij.vcs.log.data.index.VcsLogPathsIndex.ChangeKind.RENAMED_TO
import org.junit.Test
import kotlin.test.*

class VcsLogPathsForwardIndexTest {
  @Test
  fun simpleCommitConversion() {
    val source = mapOf(0 to listOf(MODIFIED),
                       1 to listOf(ChangeData(RENAMED_TO, 2)),
                       2 to listOf(ChangeData(RENAMED_FROM, 1)),
                       3 to listOf(NOT_CHANGED))
    val dest = listOf(setOf(0, 1, 2))
    val actual = VcsLogPathsForwardIndex.convertToMapValueType(source)
    assertEquals(dest, actual)
  }

  @Test
  fun mergeCommitConversion() {
    val source = mapOf(0 to listOf(MODIFIED, MODIFIED),
                       1 to listOf(NOT_CHANGED, ChangeData(RENAMED_TO, 2)),
                       2 to listOf(NOT_CHANGED, ChangeData(RENAMED_FROM, 1)),
                       3 to listOf(NOT_CHANGED, MODIFIED))
    val dest = listOf(setOf(0), setOf(0, 1, 2, 3))
    val actual = VcsLogPathsForwardIndex.convertToMapValueType(source)
    assertEquals(dest, actual)
  }

  @Test
  fun newCommitDiff() {
    val id = 0
    val diffBuilder = VcsLogPathsForwardIndex.VcsLogPathsDiffBuilder(id, null)
    val newData = mapOf(0 to listOf(MODIFIED),
                        1 to listOf(ChangeData(RENAMED_TO, 2)),
                        2 to listOf(ChangeData(RENAMED_FROM, 1)),
                        3 to listOf(NOT_CHANGED))
    val addProcessor = CollectingProcessor(id)
    val updateProcessor = CollectingProcessor(id)
    assertTrue(diffBuilder.differentiate(newData, addProcessor, updateProcessor, AssertFalseRemoveProcessor()))
    assertEquals(newData, addProcessor.result)
    assertEquals(hashMapOf(), updateProcessor.result)
  }

  @Test
  fun reindexWithRenamesDiff() {
    val id = 0
    val oldData = mapOf(0 to listOf(MODIFIED),
                        1 to listOf(MODIFIED),
                        2 to listOf(MODIFIED))
    val newData = mapOf(0 to listOf(MODIFIED),
                        1 to listOf(ChangeData(RENAMED_TO, 2)),
                        2 to listOf(ChangeData(RENAMED_FROM, 1)))
    val diffBuilder = VcsLogPathsForwardIndex.VcsLogPathsDiffBuilder(id, VcsLogPathsForwardIndex.convertToMapValueType(oldData))
    val addProcessor = CollectingProcessor(id)
    val updateProcessor = CollectingProcessor(id)
    assertFalse(diffBuilder.differentiate(newData, addProcessor, updateProcessor, AssertFalseRemoveProcessor()))
    assertEquals(hashMapOf(), addProcessor.result)
    assertEquals(hashMapOf(), updateProcessor.result)
  }

  private class CollectingProcessor(private val id: Int) : KeyValueUpdateProcessor<Int, List<ChangeData>> {
    val result = mutableMapOf<Int, List<ChangeData>>()
    override fun process(key: Int?, value: List<ChangeData>?, inputId: Int) {
      assertEquals(id, inputId)
      assertNotNull(key)
      assertNotNull(value)

      result[key!!] = value!!
    }
  }

  private class AssertFalseRemoveProcessor : RemovedKeyProcessor<Int> {
    override fun process(key: Int?, inputId: Int) {
      fail("Removed $key for id $inputId")
    }
  }
}