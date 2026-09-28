// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import com.intellij.util.containers.ConcurrentLongIntMap
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class ConcurrentLongIntHashMapTest {
  @Test
  fun `put and get`() {
    val map = ConcurrentCollectionFactory.createConcurrentLongIntMap(0)
    assertThat(map.size()).isZero()
    assertThat(map.isEmpty).isTrue()
    assertThat(map.put(1, 2)).isZero()
    assertThat(map.put(2, 3)).isZero()
    assertThat(map.size()).isEqualTo(2)
    assertThat(map.isEmpty).isFalse()
    assertThat(map.get(1)).isEqualTo(2)
    assertThat(map.get(2)).isEqualTo(3)
    assertThat(map.getOrDefault(2, 4)).isEqualTo(3)
    assertThat(map.get(3)).isZero()
    assertThat(map.getOrDefault(3, 4)).isEqualTo(4)
    assertThat(map.containsKey(1)).isTrue()
    assertThat(map.containsKey(3)).isFalse()
    assertThat(map.containsValue(3)).isTrue()
    assertThat(map.containsValue(4)).isFalse()
    assertThat(map.put(2, 4)).isEqualTo(3)
    assertThat(map.get(2)).isEqualTo(4)
    assertThat(map.size()).isEqualTo(2)
  }

  @Test
  fun `zero is distinct from the default value`() {
    val map = ConcurrentCollectionFactory.createConcurrentLongIntMap(-1)

    assertThat(map.get(1)).isEqualTo(-1)
    assertThat(map.put(1, 0)).isEqualTo(-1)
    assertThat(map.get(1)).isZero()
    assertThat(map.getOrDefault(1, 2)).isZero()
    assertThat(map.containsKey(1)).isTrue()
    assertThat(map.remove(1)).isZero()
    assertThat(map.get(1)).isEqualTo(-1)
    assertThatIllegalArgumentException().isThrownBy { map.put(2, -1) }
  }

  @Test
  fun `stores zero key in a tree bin`() {
    val map = ConcurrentCollectionFactory.createConcurrentLongIntMap(0)
    assertThat(map.put(0, 1)).isZero()
    for (value in 1..100) {
      val key = (value.toLong() shl 32) or value.toLong()
      assertThat(map.put(key, value + 1)).isZero()
    }

    assertThat(map.get(0)).isEqualTo(1)
    assertThat(map.remove(0)).isEqualTo(1)
    assertThat(map.containsKey(0)).isFalse()
  }

  @Test
  fun remove() {
    val map = createFrom(1L to 2, 2L to 3, 3L to 4)
    assertThat(map.size()).isEqualTo(3)
    assertThat(map.remove(4)).isZero()
    assertThat(map.remove(3, 5)).isFalse()
    assertThat(map.size()).isEqualTo(3)

    assertThat(map.remove(3)).isEqualTo(4)
    assertThat(map.size()).isEqualTo(2)
    assertThat(map.remove(2, 3)).isTrue()
    assertThat(map.size()).isEqualTo(1)
    map.clear()
    assertThat(map.isEmpty).isTrue()
  }

  @Test
  fun entries() {
    val map = createFrom(1L to 2, 2L to 3, 3L to 4)
    assertThat(map.entrySet().map { it.key to it.value }).containsExactlyInAnyOrder(1L to 2, 2L to 3, 3L to 4)
  }

  @Test
  fun `put if absent`() {
    val map = createFrom(1L to 2)
    assertThat(map.putIfAbsent(1, 3)).isEqualTo(2)
    assertThat(map[1]).isEqualTo(2)
    assertThat(map.putIfAbsent(2, 3)).isZero()
    assertThat(map[2]).isEqualTo(3)
  }

  @Test
  fun `compute if absent`() {
    val map = createFrom(1L to 2)
    assertThat(map.computeIfAbsent(1) { 3 }).isEqualTo(2)
    assertThat(map[1]).isEqualTo(2)
    assertThat(map.computeIfAbsent(2) { 3 }).isEqualTo(3)
    assertThat(map[2]).isEqualTo(3)
  }

  @Test
  fun replace() {
    val map = createFrom(1L to 2, 2L to 3)
    assertThat(map.replace(3, 4)).isZero()
    assertThat(map.replace(2, 4)).isEqualTo(3)
    assertThat(map[2]).isEqualTo(4)
    assertThat(map.replace(1, 3, 4)).isFalse()
    assertThat(map.replace(1, 2, 3)).isTrue()
    assertThat(map[1]).isEqualTo(3)
  }

  @Test
  fun `stores full-width long keys separately`() {
    val map = ConcurrentCollectionFactory.createConcurrentLongIntMap(0)
    val firstKey = 1L
    val secondKey = firstKey + (1L shl 32)

    assertThat(map.put(firstKey, 11)).isZero()
    assertThat(map.put(secondKey, 22)).isZero()
    assertThat(map.get(firstKey)).isEqualTo(11)
    assertThat(map.get(secondKey)).isEqualTo(22)
    assertThat(map.size()).isEqualTo(2)
  }

  @Test
  fun `uses int values in update methods`() {
    val map = ConcurrentCollectionFactory.createConcurrentLongIntMap(0)
    val key = 1L shl 40

    assertThat(map.computeIfAbsent(key) { actualKey ->
      assertThat(actualKey).isEqualTo(key)
      7
    }).isEqualTo(7)
    assertThat(map.computeIfPresent(key) { actualKey, value ->
      assertThat(actualKey).isEqualTo(key)
      value + 3
    }).isEqualTo(10)
    assertThat(map.compute(key) { actualKey, value ->
      assertThat(actualKey).isEqualTo(key)
      value + 5
    }).isEqualTo(15)
    assertThat(map.merge(key, 5, Int::plus)).isEqualTo(20)
    assertThat(map.remove(key)).isEqualTo(20)
  }

  private fun createFrom(vararg pairs: Pair<Long, Int>): ConcurrentLongIntMap {
    val map = ConcurrentCollectionFactory.createConcurrentLongIntMap(0)
    for ((key, value) in pairs) {
      map.put(key, value)
    }
    return map
  }
}
