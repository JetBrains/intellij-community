// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.testFramework.junit5.TestApplication
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@TestApplication
class BulkArrayQueueTest {

  @Test
  fun `test bulk enqueue first`() {
    val queue = BulkArrayQueue<Int>()
    val initialCapacity = queue.capacity()
    val arrayList = ObjectArrayList((0 until initialCapacity - 2).toList())
    val tail = arrayList.size

    val totalSize = arrayList.size + 1

    repeat(initialCapacity * 2) {
      queue.enqueue(tail)
      assertEquals(1, queue.size())
      assertEquals(initialCapacity, queue.capacity())

      queue.bulkEnqueueFirst(arrayList)
      assertEquals(totalSize, queue.size())
      assertEquals(initialCapacity, queue.capacity())

      for (j in 0 until  totalSize) {
        val item = queue.pollFirst()
        assertEquals(j, item)
        assertEquals(totalSize - j - 1, queue.size())
      }

      assertEquals(0, queue.size())
      assertEquals(initialCapacity, queue.capacity())
    }
  }

  @Test
  fun `test bulk enqueue first_with_grow`() {
    val queue = BulkArrayQueue<Int>()
    val initialCapacity = queue.capacity()
    val arrayList = ObjectArrayList((0 until initialCapacity + 2).toList())

    val tail = arrayList.size
    val totalSize = arrayList.size + 1

    queue.enqueue(tail)
    assertEquals(1, queue.size())

    queue.bulkEnqueueFirst(arrayList)
    assertEquals(totalSize, queue.size())
    assertNotEquals(initialCapacity, queue.capacity())

    for (j in 0 until totalSize) {
      val item = queue.pollFirst()
      assertEquals(j, item)
      assertEquals(totalSize - j - 1, queue.size())
    }

    assertEquals(0, queue.size())
  }
}