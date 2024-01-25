// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.containers

import com.intellij.platform.workspace.storage.impl.containers.MutableNonNegativeIntIntBiMap
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NonNegativeIntIntBiMapTest {
  @Test
  fun `add and replace value`() {
    val map = MutableNonNegativeIntIntBiMap()

    map.addAll(intArrayOf(1), 10)
    map.addAll(intArrayOf(1), 20)

    assertArrayEquals(intArrayOf(1), map.getKeys(20).toArray())
    assertArrayEquals(intArrayOf(), map.getKeys(10).toArray())
  }

  @Test
  fun `add different keys with the same value`() {
    val map = MutableNonNegativeIntIntBiMap()

    map.addAll(intArrayOf(1), 10)
    map.addAll(intArrayOf(2), 10)

    assertArrayEquals(intArrayOf(1, 2), map.getKeys(10).toArray())

    assertEquals(10, map.get(1))
    assertEquals(10, map.get(2))
  }

  @Test
  fun `add different keys with the same value 3`() {
    val map = MutableNonNegativeIntIntBiMap()

    map.addAll(intArrayOf(1, 1), 10)

    assertEquals(10, map.get(1))
  }
}