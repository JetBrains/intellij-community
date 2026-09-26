// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConcurrentHashMapTest {
  @Test
  fun `put and get`() {
    val map = ConcurrentCollectionFactory.createConcurrentMap<Int,Int>()
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
    val map = ConcurrentCollectionFactory.createConcurrentMap<Int,Int>()
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
    val map = ConcurrentCollectionFactory.createConcurrentMap<Int, Int>().also { it.putAll(mapOf(1 to 2, 2 to 3, 3 to 4)) }
    assertThat(map.entries.map { it.key to it.value }).containsExactlyInAnyOrder(1 to 2, 2 to 3, 3 to 4)
    assertThat(map.keys).containsExactlyInAnyOrder(1, 2, 3)
    assertThat(map.values).containsExactlyInAnyOrder(2, 3, 4)
  }

  @Test
  fun `put if absent`() {
    val map = ConcurrentCollectionFactory.createConcurrentMap<Int, Int>().also { it.putAll(mapOf(1 to 2)) }
    assertThat(map.putIfAbsent(1, 3)).isEqualTo(2)
    assertThat(map[1]).isEqualTo(2)
    assertThat(map.putIfAbsent(2, 3)).isNull()
    assertThat(map[2]).isEqualTo(3)
  }

  @Test
  fun `compute if absent`() {
    val map = ConcurrentCollectionFactory.createConcurrentMap<Int, Int>().also { it.putAll(mapOf(1 to 2)) }
    assertThat(map.computeIfAbsent(1) { 3 }).isEqualTo(2)
    assertThat(map[1]).isEqualTo(2)
    assertThat(map.computeIfAbsent(2) { 3 }).isEqualTo(3)
    assertThat(map[2]).isEqualTo(3)
  }

  @Test
  fun `compute if present`() {
    val map = ConcurrentCollectionFactory.createConcurrentMap<Int, Int>().also { it.putAll(mapOf(1 to 2)) }
    assertThat(map.computeIfPresent(2) { _, _ -> 3 }).isNull()
    assertThat(map[1]).isEqualTo(2)
    assertThat(map.computeIfPresent(1) { _, _ -> 3 }).isEqualTo(3)
    assertThat(map[1]).isEqualTo(3)
  }

  @Test
  fun compute() {
    val map = ConcurrentCollectionFactory.createConcurrentMap<Int, Int>().also { it.putAll(mapOf(1 to 2)) }
    assertThat(map.compute(1) { _, _ -> 3 }).isEqualTo(3)
    assertThat(map[1]).isEqualTo(3)
    assertThat(map.compute(2) { _, _ -> 3 }).isEqualTo(3)
    assertThat(map[2]).isEqualTo(3)
  }

  @Test
  fun replace() {
    val map = ConcurrentCollectionFactory.createConcurrentMap<Int, Int>().also { it.putAll(mapOf(1 to 2, 2 to 3)) }
    assertThat(map.replace(3, 4)).isNull()
    assertThat(map.replace(2, 4)).isEqualTo(3)
    assertThat(map[2]).isEqualTo(4)
    assertThat(map.replace(1, 3, 4)).isFalse()
    assertThat(map.replace(1, 2, 3)).isTrue()
    assertThat(map[1]).isEqualTo(3)
  }

  @Test
  fun `replace all`() {
    val map = ConcurrentCollectionFactory.createConcurrentMap<Int, Int>().also { it.putAll(mapOf(1 to 2, 2 to 3)) }
    map.replaceAll { k, v -> k + v}
    assertThat(map).isEqualTo(mapOf(1 to 3, 2 to 5))
  }

  @Test
  fun merge() {
    val map = ConcurrentCollectionFactory.createConcurrentMap<Int, Int>().also { it.putAll(mapOf(1 to 2, 2 to 3)) }
    assertThat(map.merge(3, 4) { _, _ -> 4 }).isEqualTo(4)
    assertThat(map[3]).isEqualTo(4)
    assertThat(map.merge(1, 3) { a, b -> a+b }).isEqualTo(5)
    assertThat(map[1]).isEqualTo(5)
  }
}