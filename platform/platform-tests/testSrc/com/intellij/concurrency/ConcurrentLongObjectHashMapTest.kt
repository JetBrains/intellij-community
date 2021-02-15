// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.concurrency

import org.assertj.core.api.Assertions.*
import org.junit.Assert.assertNull
import org.junit.Test

class ConcurrentLongObjectHashMapTest {
  @Test
  fun `put and get`() {
    val map = ConcurrentLongObjectHashMap<Int>()
    assertThat(map.size()).isEqualTo(0)
    assertThat(map.isEmpty).isTrue()
    map.put(1, 0)
    map.put(2, 1)
    assertThat(map.size()).isEqualTo(2)
    assertThat(map.isEmpty).isFalse()
    assertThat(map.get(1)).isEqualTo(0)
    assertThat(map.get(2)).isEqualTo(1)
    assertThat(map.getOrDefault(2, 4)).isEqualTo(1)
    assertNull(map.get(3))
    assertThat(map.getOrDefault(3, 4)).isEqualTo(4)
    assertThat(map.containsKey(1)).isTrue()
    assertThat(map.containsKey(3)).isFalse()
    assertThat(map.containsValue(1)).isTrue()
    assertThat(map.containsValue(2)).isFalse()
    map.put(2, 2)
    assertThat(map.get(2)).isEqualTo(2)
    assertThat(map.size()).isEqualTo(2)
  }

  @Test
  fun remove() {
    val map = createFrom(1 to 2, 2 to 3, 3 to 4)
    assertThat(map.size()).isEqualTo(3)
    assertNull(map.remove(4))
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
    val map = createFrom(1 to 2, 2 to 3, 3 to 4)
    assertThat(map.entrySet().map { it.key to it.value }).containsExactlyInAnyOrder(1L to 2, 2L to 3, 3L to 4)
    assertThat(map.keys()).containsExactlyInAnyOrder(1, 2, 3)
    assertThat(map.values()).containsExactlyInAnyOrder(2, 3, 4)
  }

  @Test
  fun `put if absent`() {
    val map = createFrom(1 to 2)
    assertThat(map.putIfAbsent(1, 3)).isEqualTo(2)
    assertThat(map[1]).isEqualTo(2)
    assertNull(map.putIfAbsent(2, 3))
    assertThat(map[2]).isEqualTo(3)
  }

  @Test
  fun replace() {
    val map = createFrom(1 to 2, 2 to 3)
    assertNull(map.replace(3, 4))
    assertThat(map.replace(2, 4)).isEqualTo(3)
    assertThat(map[2]).isEqualTo(4)
    assertThat(map.replace(1, 3, 4)).isFalse()
    assertThat(map.replace(1, 2, 3)).isTrue()
    assertThat(map[1]).isEqualTo(3)
  }

  private fun createFrom(vararg pair: Pair<Int, Int>): ConcurrentLongObjectHashMap<Int> {
    val map = ConcurrentLongObjectHashMap<Int>()
    for (entry in pair) {
      map.put(entry.first.toLong(), entry.second)
    }
    return map
  }
}