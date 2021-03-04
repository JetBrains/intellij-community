// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.containers

import com.intellij.workspaceModel.storage.impl.containers.MutableIntIntUniqueBiMap
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class IntIntUniqueBiMapTest {

  lateinit var map: MutableIntIntUniqueBiMap

  @Before
  fun setUp() {
    map = MutableIntIntUniqueBiMap()
  }

  @Test
  fun `put and get`() {
    map.put(1, 2)
    assertEquals(2, map.get(1))
    assertEquals(1, map.getKey(2))
  }

  @Test(expected = IllegalStateException::class)
  fun `put twice`() {
    map.put(1, 2)
    map.put(1, 3)
  }

  @Test(expected = IllegalStateException::class)
  fun `put twice same value`() {
    map.put(1, 2)
    map.put(10, 2)
  }

  @Test
  fun `put force and get`() {
    map.put(1, 2)
    map.putForce(1, 3)

    assertEquals(3, map.get(1))
    assertEquals(1, map.getKey(3))
    assertEquals(0, map.getKey(2))
    assertFalse(map.containsKey(2))
  }

  @Test
  fun `put force same value`() {
    map.put(1, 2)
    map.putForce(10, 2)

    assertEquals(2, map.get(10))
    assertEquals(10, map.getKey(2))
    assertTrue(map.containsValue(2))
    assertFalse(map.containsKey(1))
  }
}