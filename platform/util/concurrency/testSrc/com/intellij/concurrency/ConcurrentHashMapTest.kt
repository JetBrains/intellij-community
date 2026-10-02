// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import com.intellij.util.containers.HashingStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ConcurrentHashMapTest {
  @Test
  fun `put and get`() {
    val map = ConcurrentHashMap<Int, Int>()
    assertThat(map.size).isEqualTo(0)
    assertThat(map.isEmpty()).isTrue()
    map[1] = 0
    map[2] = 1
    assertThat(map.size).isEqualTo(2)
    assertThat(map.isEmpty()).isFalse()
    assertThat(map[1]).isEqualTo(0)
    assertThat(map[2]).isEqualTo(1)
    assertThat(map.getOrDefault(2, 4)).isEqualTo(1)
    assertThat(map[3]).isNull()
    assertThat(map.getOrDefault(3, 4)).isEqualTo(4)
    assertThat(map.containsKey(1)).isTrue()
    assertThat(map.containsKey(3)).isFalse()
    assertThat(map.containsValue(1)).isTrue()
    assertThat(map.containsValue(2)).isFalse()
    map[2] = 2
    assertThat(map[2]).isEqualTo(2)
    assertThat(map.size).isEqualTo(2)
  }

  @Test
  fun remove() {
    val map = ConcurrentHashMap<Int, Int>()
    map.putAll(mapOf(1 to 2, 2 to 3, 3 to 4))
    assertThat(map.size).isEqualTo(3)
    assertThat(map.remove(4)).isNull()
    assertThat(map.remove(3, 5)).isFalse()
    assertThat(map.size).isEqualTo(3)

    assertThat(map.remove(3)).isEqualTo(4)
    assertThat(map.size).isEqualTo(2)
    assertThat(map.remove(2, 3)).isTrue()
    assertThat(map.size).isEqualTo(1)
    map.clear()
    assertThat(map.isEmpty()).isTrue()
  }

  @Test
  fun entries() {
    val map = ConcurrentHashMap<Int, Int>().also { it.putAll(mapOf(1 to 2, 2 to 3, 3 to 4)) }
    assertThat(map.entries.map { it.key to it.value }).containsExactlyInAnyOrder(1 to 2, 2 to 3, 3 to 4)
    assertThat(map.keys).containsExactlyInAnyOrder(1, 2, 3)
    assertThat(map.values).containsExactlyInAnyOrder(2, 3, 4)
  }

  @Test
  fun `put if absent`() {
    val map = ConcurrentHashMap<Int, Int>().also { it.putAll(mapOf(1 to 2)) }
    assertThat(map.putIfAbsent(1, 3)).isEqualTo(2)
    assertThat(map[1]).isEqualTo(2)
    assertThat(map.putIfAbsent(2, 3)).isNull()
    assertThat(map[2]).isEqualTo(3)
  }

  @Test
  fun `compute if absent`() {
    val map = ConcurrentHashMap<Int, Int>().also { it.putAll(mapOf(1 to 2)) }
    assertThat(map.computeIfAbsent(1) { 3 }).isEqualTo(2)
    assertThat(map[1]).isEqualTo(2)
    assertThat(map.computeIfAbsent(2) { 3 }).isEqualTo(3)
    assertThat(map[2]).isEqualTo(3)
  }

  @Test
  fun `compute if present`() {
    val map = ConcurrentHashMap<Int, Int>().also { it.putAll(mapOf(1 to 2)) }
    assertThat(map.computeIfPresent(2) { _, _ -> 3 }).isNull()
    assertThat(map[1]).isEqualTo(2)
    assertThat(map.computeIfPresent(1) { _, _ -> 3 }).isEqualTo(3)
    assertThat(map[1]).isEqualTo(3)
  }

  @Test
  fun compute() {
    val map = ConcurrentHashMap<Int, Int>().also { it.putAll(mapOf(1 to 2)) }
    assertThat(map.compute(1) { _, _ -> 3 }).isEqualTo(3)
    assertThat(map[1]).isEqualTo(3)
    assertThat(map.compute(2) { _, _ -> 3 }).isEqualTo(3)
    assertThat(map[2]).isEqualTo(3)
  }

  @Test
  fun replace() {
    val map = ConcurrentHashMap<Int, Int>().also { it.putAll(mapOf(1 to 2, 2 to 3)) }
    assertThat(map.replace(3, 4)).isNull()
    assertThat(map.replace(2, 4)).isEqualTo(3)
    assertThat(map[2]).isEqualTo(4)
    assertThat(map.replace(1, 3, 4)).isFalse()
    assertThat(map.replace(1, 2, 3)).isTrue()
    assertThat(map[1]).isEqualTo(3)
  }

  @Test
  fun `replace all`() {
    val map = ConcurrentHashMap<Int, Int>().also { it.putAll(mapOf(1 to 2, 2 to 3)) }
    map.replaceAll { k, v -> k + v }
    assertThat(map).isEqualTo(mapOf(1 to 3, 2 to 5))
  }

  @Test
  fun merge() {
    val map = ConcurrentHashMap<Int, Int>().also { it.putAll(mapOf(1 to 2, 2 to 3)) }
    assertThat(map.merge(3, 4) { _, _ -> 4 }).isEqualTo(4)
    assertThat(map[3]).isEqualTo(4)
    assertThat(map.merge(1, 3) { a, b -> a + b }).isEqualTo(5)
    assertThat(map[1]).isEqualTo(5)
  }

  @Test
  fun `put all of an empty map keeps the table uninitialized`() {
    val map = ConcurrentHashMap<Int, Int>()
    map.putAll(emptyMap())

    assertThat(map.table).isNull()
    assertThat(map).isEmpty()

    map.putAll(mapOf(1 to 2))
    assertThat(map.table).hasSize(16)
    assertThat(map[1]).isEqualTo(2)
  }

  @Test
  fun `copy a map uses capacity for its size`() {
    val map = ConcurrentHashMap(mapOf(1 to 2))

    assertThat(map.table).hasSize(2)
    assertThat(map).containsExactlyEntriesOf(mapOf(1 to 2))
  }

  @Test
  fun `custom hashing supports concurrent updates and resizing`() {
    val strategy = object : HashingStrategy<Key> {
      override fun hashCode(key: Key): Int = key.id % 8
      override fun equals(key1: Key, key2: Key): Boolean = key1.id == key2.id
    }
    val map = ConcurrentHashMap<Key, Int>(64, 0.75f, 1, strategy)
    val ready = CountDownLatch(4)
    val start = CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(4) { task ->
      Thread(task, "ConcurrentHashMapTest").apply { isDaemon = true }
    }
    try {
      val futures = List(4) {
        executor.submit {
          ready.countDown()
          check(start.await(10, TimeUnit.SECONDS))
          repeat(512) { id ->
            map.compute(Key(id)) { _, value -> (value ?: 0) + 1 }
            check(map[Key(id)] != null)
          }
        }
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue()
      start.countDown()
      futures.forEach { it.get(10, TimeUnit.SECONDS) }

      assertThat(map).hasSize(512)
      repeat(512) { id ->
        assertThat(map[Key(id)]).isEqualTo(4)
      }
      repeat(504) { id ->
        assertThat(map.remove(Key(id))).isEqualTo(4)
      }
      assertThat(map.keys.map { it.id }).containsExactlyInAnyOrderElementsOf(504 until 512)
      for (id in 504 until 512) {
        assertThat(map[Key(id)]).isEqualTo(4)
      }
    }
    finally {
      start.countDown()
      executor.shutdownNow()
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
    }
  }

  private class Key(val id: Int) : Comparable<Key> {
    override fun compareTo(other: Key): Int = id.compareTo(other.id)
  }
}
