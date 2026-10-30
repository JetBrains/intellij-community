// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.testFramework.junit5.TestApplication
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.junit.Test
import java.util.ArrayDeque
import java.util.NoSuchElementException
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestApplication
class BulkArrayQueueTest {

  @Test
  fun `test initial capacity must be positive`() {
    assertFailsWith<IllegalArgumentException> { BulkArrayQueue<Int>(0) }
    assertFailsWith<IllegalArgumentException> { BulkArrayQueue<Int>(-1) }

    val queue = BulkArrayQueue<Int>(1)
    queue.addFirst(1)
    queue.addLast(2)
    assertEquals(2, queue.removeLast())
    assertEquals(1, queue.pollFirst())
  }

  @Test
  fun `test removal preserves order across wraparound and growth`() {
    val queue = BulkArrayQueue<Int>(4)
    repeat(3) { queue.addLast(it) }
    assertEquals(0, queue.pollFirst())
    assertEquals(1, queue.pollFirst())
    queue.addLast(3)
    queue.addLast(4)

    queue.removeAll { it == 2 }
    assertEquals(listOf(3, 4), (0 until queue.size()).map { queue.elementAt(it) })

    queue.addFirst(2)
    queue.addLast(5)
    queue.removeAll { it % 2 == 0 }
    assertEquals(listOf(3, 5), (0 until queue.size()).map { queue.elementAt(it) })

    queue.removeAll { true }
    assertTrue(queue.isEmpty)
    queue.addFirst(42)
    assertEquals(42, queue.removeLast())
  }

  @Test
  fun `test add first wraps and grows`() {
    val queue = BulkArrayQueue<Int>(10)
    val initialCapacity = queue.capacity()
    val totalSize = initialCapacity * 3

    repeat(totalSize) {
      queue.addFirst(it)
      assertEquals(it, queue.peekFirst())
      assertEquals(it + 1, queue.size())
    }

    assertTrue(queue.capacity() > initialCapacity)
    repeat(totalSize) {
      assertEquals(totalSize - it - 1, queue.elementAt(it))
    }
    repeat(totalSize) {
      assertEquals(totalSize - it - 1, queue.pollFirst())
    }
    assertTrue(queue.isEmpty)
  }

  @Test
  fun `test remove last wraps and empties the queue`() {
    val queue = BulkArrayQueue<Int>(10)
    val initialCapacity = queue.capacity()

    repeat(initialCapacity * 2) {
      queue.addLast(it * 2)
      queue.addLast(it * 2 + 1)
      assertEquals(it * 2, queue.pollFirst())
      assertEquals(it * 2 + 1, queue.removeLast())
      assertEquals(0, queue.size())
      assertTrue(queue.isEmpty)
      assertNull(queue.peekFirst())
    }
    assertEquals(initialCapacity, queue.capacity())
  }

  @Test
  fun `test element at rejects invalid indices`() {
    val queue = BulkArrayQueue<Int>(10)

    assertFailsWith<IndexOutOfBoundsException> { queue.elementAt(0) }
    queue.addFirst(42)
    assertFailsWith<IndexOutOfBoundsException> { queue.elementAt(-1) }
    assertFailsWith<IndexOutOfBoundsException> { queue.elementAt(queue.size()) }
    assertFailsWith<IndexOutOfBoundsException> { queue.elementAt(Int.MAX_VALUE) }
    assertEquals(42, queue.elementAt(0))
    assertEquals(1, queue.size())
    assertEquals(42, queue.removeLast())
    assertFailsWith<IndexOutOfBoundsException> { queue.elementAt(0) }
  }

  @Test
  fun `test remove last rejects an empty queue`() {
    val queue = BulkArrayQueue<Int>(10)

    assertFailsWith<NoSuchElementException> { queue.removeLast() }
    queue.addFirst(42)
    assertEquals(42, queue.removeLast())
    assertFailsWith<NoSuchElementException> { queue.removeLast() }
    assertTrue(queue.isEmpty)
    queue.addLast(43)
    assertEquals(43, queue.pollFirst())
  }

  @Test
  fun `test peek last preserves the queue across wraparound and growth`() {
    val queue = BulkArrayQueue<Int>(4)
    assertNull(queue.peekLast())

    queue.addFirst(1)
    assertEquals(1, queue.peekLast())
    assertEquals(1, queue.peekLast())
    assertEquals(1, queue.size())

    queue.addLast(2)
    assertEquals(2, queue.peekLast())
    queue.addFirst(0)
    assertEquals(2, queue.peekLast())
    queue.addLast(3)
    assertEquals(3, queue.peekLast())
    assertEquals(4, queue.size())

    assertEquals(3, queue.removeLast())
    assertEquals(2, queue.peekLast())
    repeat(3) { assertEquals(it, queue.pollFirst()) }
    assertNull(queue.peekLast())
    assertEquals(0, queue.size())
  }

  @Test
  fun `test mixed operations match a deque`() {
    val queue = BulkArrayQueue<Int>(10)
    val expected = ArrayDeque<Int>()
    val random = Random(0)

    repeat(queue.capacity() * 10) { value ->
      when (random.nextInt(6)) {
        0 -> {
          queue.addFirst(value)
          expected.addFirst(value)
        }
        1 -> {
          queue.addLast(value)
          expected.addLast(value)
        }
        2 -> assertEquals(expected.pollFirst(), queue.pollFirst())
        3 -> if (expected.isNotEmpty()) {
          assertEquals(expected.removeLast(), queue.removeLast())
        }
        else -> {
          val elements = listOf(value, -value)
          queue.bulkEnqueueFirst(ObjectArrayList(elements))
          elements.asReversed().forEach { expected.addFirst(it) }
        }
      }
      assertEquals(expected.size, queue.size())
      assertEquals(expected.peekFirst(), queue.peekFirst())
      assertEquals(expected.peekLast(), queue.peekLast())
      expected.forEachIndexed { index, element ->
        assertEquals(element, queue.elementAt(index))
      }
    }

    while (expected.isNotEmpty()) {
      assertEquals(expected.removeLast(), queue.removeLast())
    }
    assertTrue(queue.isEmpty)
  }

  @Test
  fun `test bulk enqueue first`() {
    val queue = BulkArrayQueue<Int>(10)
    val initialCapacity = queue.capacity()
    val arrayList = ObjectArrayList((0 until initialCapacity - 2).toList())
    val tail = arrayList.size

    val totalSize = arrayList.size + 1

    repeat(initialCapacity * 2) {
      queue.addLast(tail)
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
    val queue = BulkArrayQueue<Int>(10)
    val initialCapacity = queue.capacity()
    val arrayList = ObjectArrayList((0 until initialCapacity + 2).toList())

    val tail = arrayList.size
    val totalSize = arrayList.size + 1

    queue.addLast(tail)
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
