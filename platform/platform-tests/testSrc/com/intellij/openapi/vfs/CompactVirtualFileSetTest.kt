// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs

import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.TempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class CompactVirtualFileSetTest : BareTestFixtureTestCase() {
  @JvmField
  @Rule
  var tempDir = TempDirectory()

  @Test
  fun `test empty set`() {
    val set = CompactVirtualFileSet()
    assertTrue(set.isEmpty())
    assertFalse(set.iterator().hasNext())
  }

  @Test
  fun `test small set`() {
    val size = 5

    assertTrue(size < CompactVirtualFileSet.INT_SET_LIMIT)
    assertTrue(size < CompactVirtualFileSet.BIT_SET_LIMIT)
    doSimpleAddTest(size)
  }

  @Test
  fun `test reasonable set`() {
    val size = 50

    assertTrue(size > CompactVirtualFileSet.INT_SET_LIMIT)
    assertTrue(size < CompactVirtualFileSet.BIT_SET_LIMIT)
    doSimpleAddTest(size)
  }

  @Test
  fun `test big set`() {
    val size = 2000

    assertTrue(size > CompactVirtualFileSet.INT_SET_LIMIT)
    assertTrue(size > CompactVirtualFileSet.BIT_SET_LIMIT)
    doSimpleAddTest(size)
  }

  @Test
  fun `test addAll()`() {
    doSimpleAddAllTest(sliceSize = 3)
  }

  @Test
  fun `test reasonable addAll()`() {
    doSimpleAddAllTest(sliceSize = 50)
  }

  @Test
  fun `test big addAll()`() {
    doSimpleAddAllTest(sliceSize = 777)
  }

  @Test
  fun `test retainAll() of standard collection`() {

  }

  @Test
  fun `test retainAll() of small CVFSet`() {
    val size = 5
    assertTrue(size < CompactVirtualFileSet.INT_SET_LIMIT)
    assertTrue(size < CompactVirtualFileSet.BIT_SET_LIMIT)

  }

  @Test
  fun `test retainAll() of reasonable CVFSet`() {
    val size = 50

    assertTrue(size > CompactVirtualFileSet.INT_SET_LIMIT)
    assertTrue(size < CompactVirtualFileSet.BIT_SET_LIMIT)
  }

  @Test
  fun `test retainAll() of big CVFSet`() {
    val size = 2000

    assertTrue(size > CompactVirtualFileSet.INT_SET_LIMIT)
    assertTrue(size > CompactVirtualFileSet.BIT_SET_LIMIT)
  }

  private fun doSimpleAddAllTest(sliceSize: Int) {
    val set1 = CompactVirtualFileSet()
    val set2 = CompactVirtualFileSet()

    val fileList1 = (0 until sliceSize).map { createFile() }
    val fileList2 = (0 until sliceSize).map { createFile() }
    val fileList3 = (0 until sliceSize).map { createFile() }

    for (virtualFile in fileList1) {
      set1.add(virtualFile)
    }
    for (virtualFile in fileList2) {
      set1.add(virtualFile)
      set2.add(virtualFile)
    }
    for (virtualFile in fileList3) {
      set2.add(virtualFile)
    }
    assertEquals(2 * sliceSize, set1.size)
    assertEquals(2 * sliceSize, set2.size)

    set1.addAll(set2)
    assertEquals(3 * sliceSize, set1.size)

    for (file in fileList1 + fileList2 + fileList3) {
      assertTrue(set1.contains(file))
    }
  }

  private fun doSimpleAddTest(size: Int) {
    val set = CompactVirtualFileSet()

    val fileList = (0 until size).map { createFile() }

    for (virtualFile in fileList) {
      set.add(virtualFile)
    }

    assertEquals(size, set.size)

    for (virtualFile in fileList) {
      assertTrue(set.contains(virtualFile))
    }

    for (virtualFile in set) {
      assertTrue(fileList.contains(virtualFile))
    }
  }

  private val counter = AtomicInteger()

  private fun createFile(): VirtualFile =
    tempDir.newVirtualFile("file${counter.incrementAndGet()}.txt")
}