// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.containers

import com.intellij.workspaceModel.storage.impl.containers.MutableNonNegativeIntIntBiMap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class NonNegativeIntIntBiMapTest {
  @Test
  fun `add and replace value`() {
    val map = MutableNonNegativeIntIntBiMap()

    map.putAll(intArrayOf(1), 10)
    map.putAll(intArrayOf(1), 20)

    assertArrayEquals(intArrayOf(1), map.getKeys (20).toArray())
    assertArrayEquals(intArrayOf(), map.getKeys (10).toArray())
  }

  @Test
  fun `add different keys with the same value`() {
    val map = MutableNonNegativeIntIntBiMap()

    map.putAll(intArrayOf(1), 10)
    map.putAll(intArrayOf(2), 10)

    assertArrayEquals(intArrayOf(1, 2), map.getKeys (10).toArray())

    assertEquals(10, map.get(1))
    assertEquals(10, map.get(2))
  }
}