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
    doSimpleAddTest(smallSetSize)
  }

  @Test
  fun `test reasonable set`() {
    doSimpleAddTest(reasonableSetSize)
  }

  @Test
  fun `test big set`() {
    doSimpleAddTest(bigSetSize)
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
    val set = (0 until 10).map { createFile() }.toHashSet()
    doTestRetainAll(set)
  }

  @Test
  fun `test retainAll() of small CVFSet`() {
    val set = generateCVFSet(smallSetSize)
    doTestRetainAll(set)
  }

  @Test
  fun `test retainAll() of reasonable CVFSet`() {
    val set = generateCVFSet(reasonableSetSize)
    doTestRetainAll(set)
  }

  @Test
  fun `test retainAll() of big CVFSet`() {
    val set = generateCVFSet(bigSetSize)
    doTestRetainAll(set)
  }

  @Test
  fun `test remove() from small CVSet`() {
    doTestRemove(smallSetSize)
  }

  @Test
  fun `test remove() from reasonable CVSet`() {
    doTestRemove(reasonableSetSize)
  }

  @Test
  fun `test remove() from big CVSet`() {
    doTestRemove(bigSetSize)
  }

  @Test
  fun `test iterator remove from small CVSet`() {
    doTestIteratorRemove(smallSetSize)
  }

  @Test
  fun `test iterator remove from reasonable CVSet`() {
    doTestIteratorRemove(reasonableSetSize)
  }

  @Test
  fun `test iterator remove from big CVSet`() {
    doTestIteratorRemove(bigSetSize)
  }

  @Test
  fun `test clear`() {
    val set = generateCVFSet(10)
    assertEquals(10, set.size)
    set.clear()
    assertEquals(0, set.size)
    repeat(10) {
      set.add(createFile())
    }
    assertEquals(10, set.size)
  }

  private fun doTestRetainAll(set: Set<VirtualFile>) {
    val target = generateCVFSet(10)
    target.addAll(set)
    assertEquals(set.size + 10, target.size)
    target.retainAll(set)
    assertEquals(set.size, target.size)
    assertEquals(set, target.toHashSet())
  }

  private fun doTestIteratorRemove(size: Int) {
    val set = generateCVFSet(size)
    val iterator = set.iterator()
    while (iterator.hasNext()) {
      iterator.next()
      iterator.remove()
    }
    assertEquals(0, set.size)
  }

  private fun doTestRemove(size: Int) {
    val set = generateCVFSet(size)
    val copy = set.toList()
    val removedFile = set.first()
    set.remove(removedFile)
    assertEquals(size - 1, set.size)
    for (virtualFile in copy.drop(1)) {
      assertTrue(set.contains(virtualFile))
    }
    assertFalse(set.contains(removedFile))
    assertEquals(set.toHashSet(), copy.drop(1).toHashSet())
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

  private val smallSetSize: Int
    get() {
      val size = 5
      assertTrue(size < CompactVirtualFileSet.INT_SET_LIMIT)
      assertTrue(size < CompactVirtualFileSet.BIT_SET_LIMIT)
      return size
    }

  private val reasonableSetSize: Int
    get() {
      val size = 50
      assertTrue(size > CompactVirtualFileSet.INT_SET_LIMIT)
      assertTrue(size < CompactVirtualFileSet.BIT_SET_LIMIT)
      return size
    }

  private val bigSetSize: Int
    get() {
      val size = 2000
      assertTrue(size > CompactVirtualFileSet.INT_SET_LIMIT)
      assertTrue(size > CompactVirtualFileSet.BIT_SET_LIMIT)
      return size
    }

  private fun generateCVFSet(size: Int): CompactVirtualFileSet {
    val set = CompactVirtualFileSet()
    repeat(size) { set.add(createFile()) }
    return set
  }

  private fun createFile(): VirtualFile =
    tempDir.newVirtualFile("file${counter.incrementAndGet()}.txt")
}