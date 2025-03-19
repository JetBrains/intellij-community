// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LinkedCustomHashMapTest {
  @Test
  fun accessOrderValuesIteration() {
    val map = LinkedCustomHashMap<Int, String>()
    map.put(1, "a")
    map.put(2, "b")
    map.put(3, "c")
    val iterator = map.values().iterator()
    assertThat(iterator.next()).isEqualTo("a")
    assertThat(iterator.next()).isEqualTo("b")
    assertThat(iterator.next()).isEqualTo("c")
    assertThat(iterator.hasNext()).isFalse()
  }

  @Test
  fun lru() {
    val tested = LinkedCustomHashMap<Int, String>(LinkedCustomHashMap.RemoveCallback { size, _, _, _ ->
      size > 500
    })
    for (i in 0..999) {
      tested.put(i, i.toString())
    }
    assertThat(tested.size()).isEqualTo(500)
    for (i in 0..499) {
      assertThat(tested.remove(i)).isNull()
    }
    assertThat(tested.size()).isEqualTo(500)
    for (i in 500..999) {
      assertThat(tested.remove(i)).isEqualTo(i.toString())
    }
    assertThat(tested.size()).isEqualTo(0)
  }

  @Test
  fun lru2() {
    val tested = LinkedCustomHashMap<Int, String>(LinkedCustomHashMap.RemoveCallback { size, _, _, _ ->
      size > 1000
    })
    for (i in 0..999) {
      tested.put(i, i.toString())
    }
    assertThat(tested.get(0)).isEqualTo(0.toString())
    for (i in 1000..1998) {
      tested.put(i, i.toString())
    }
    assertThat(tested.get(0)).isEqualTo(0.toString())
    tested.put(2000, 2000.toString())
    assertThat(tested.get(1000)).isNull()
  }

  @Test
  fun lru3() {
    val tested = LinkedCustomHashMap<Int, String>(LinkedCustomHashMap.RemoveCallback { size, _, _, _ ->
      size > 1000
    })
    for (i in 0..999) {
      tested.put(i, i.toString())
    }
    assertThat(tested.remove(999)).isEqualTo(999.toString())
    assertThat(tested.size()).isEqualTo(999)
    assertThat(tested.get(0)).isEqualTo(0.toString())
    for (i in 1000..1998) {
      tested.put(i, i.toString())
    }
    assertThat(tested.get(0)).isEqualTo(0.toString())
    tested.put(2000, 2000.toString())
    assertThat(tested.get(1000)).isNull()
  }
}