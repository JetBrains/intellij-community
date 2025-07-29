// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import com.intellij.util.containers.ConcurrentIntObjectMap
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConcurrentIntObjectHashMapTest {
  @Test
  fun `put and get`() {
    val map = ConcurrentCollectionFactory.createConcurrentIntObjectMap<Int>()
    assertThat(map.size()).isEqualTo(0)
    assertThat(map.isEmpty).isTrue()
    map.put(1, 0)
    map.put(2, 1)
    assertThat(map.size()).isEqualTo(2)
    assertThat(map.isEmpty).isFalse()
    assertThat(map.get(1)).isEqualTo(0)
    assertThat(map.get(2)).isEqualTo(1)
    assertThat(map.getOrDefault(2, 4)).isEqualTo(1)
    @Suppress("USELESS_CAST")
    assertThat(map.get(3) as Int?).isNull()
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
    @Suppress("USELESS_CAST")
    assertThat(map.remove(4) as Int?).isNull()
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
    assertThat(map.entrySet().map { it.key to it.value }).containsExactlyInAnyOrder(1 to 2, 2 to 3, 3 to 4)
    assertThat(map.keys()).containsExactlyInAnyOrder(1, 2, 3)
    assertThat(map.values()).containsExactlyInAnyOrder(2, 3, 4)
  }

  @Test
  fun `put if absent`() {
    val map = createFrom(1 to 2)
    @Suppress("USELESS_CAST")
    assertThat(map.putIfAbsent(1, 3) as Int?).isEqualTo(2)
    assertThat(map[1]).isEqualTo(2)
    @Suppress("USELESS_CAST")
    assertThat(map.putIfAbsent(2, 3) as Int?).isNull()
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

  private fun createFrom(vararg pair: Pair<Int, Int>): ConcurrentIntObjectMap<Int> {
    val map = ConcurrentCollectionFactory.createConcurrentIntObjectMap<Int>()
    for (entry in pair) {
      map.put(entry.first, entry.second)
    }
    return map
  }
}