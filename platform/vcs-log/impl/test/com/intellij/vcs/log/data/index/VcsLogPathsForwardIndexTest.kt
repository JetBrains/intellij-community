// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data.index

import com.intellij.util.indexing.impl.KeyValueUpdateProcessor
import com.intellij.util.indexing.impl.RemovedKeyProcessor
import com.intellij.vcs.log.data.index.VcsLogPathsIndex.ChangeKind
import com.intellij.vcs.log.data.index.VcsLogPathsIndex.ChangeKind.*
import org.junit.Test
import kotlin.test.*

class VcsLogPathsForwardIndexTest {
  @Test
  fun simpleCommitConversion() {
    val source = mapOf(0 to listOf(MODIFIED),
                       1 to listOf(REMOVED),
                       2 to listOf(ADDED),
                       3 to listOf(NOT_CHANGED))
    val dest = listOf(setOf(0, 1, 2))
    val actual = VcsLogPathsForwardIndex.convertToMapValueType(source)
    assertEquals(dest, actual)
  }

  @Test
  fun mergeCommitConversion() {
    val source = mapOf(0 to listOf(MODIFIED, MODIFIED),
                       1 to listOf(NOT_CHANGED, REMOVED),
                       2 to listOf(NOT_CHANGED, ADDED),
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
                        1 to listOf(ADDED),
                        2 to listOf(REMOVED),
                        3 to listOf(NOT_CHANGED))
    val addProcessor = CollectingProcessor(id)
    val updateProcessor = CollectingProcessor(id)
    assertTrue(diffBuilder.differentiate(newData, addProcessor, updateProcessor, AssertFalseRemoveProcessor()))
    assertEquals(newData, addProcessor.result)
    assertEquals(hashMapOf(), updateProcessor.result)
  }

  private class CollectingProcessor(private val id: Int) : KeyValueUpdateProcessor<Int, List<ChangeKind>> {
    val result = mutableMapOf<Int, List<ChangeKind>>()
    override fun process(key: Int?, value: List<ChangeKind>?, inputId: Int) {
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