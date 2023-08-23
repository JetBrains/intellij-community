// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.tests.containers

import com.intellij.platform.workspace.storage.impl.containers.MutableIntIntUniqueBiMap
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntIntUniqueBiMapTest {

  private lateinit var map: MutableIntIntUniqueBiMap

  @BeforeEach
  fun setUp() {
    map = MutableIntIntUniqueBiMap()
  }

  @Test
  fun `put and get`() {
    map.put(1, 2)
    assertEquals(2, map.get(1))
    assertEquals(1, map.getKey(2))
  }

  @Test
  fun `put twice`() {
    assertThrows<IllegalStateException> {
      map.put(1, 2)
      map.put(1, 3)
    }
  }

  @Test
  fun `put twice same value`() {
    assertThrows<IllegalStateException> {
      map.put(1, 2)
      map.put(10, 2)
    }
  }

  @Test
  fun `put force and get`() {
    map.put(1, 2)
    map.putForce(1, 3)

    assertEquals(3, map.get(1))
    assertEquals(1, map.getKey(3))
    assertEquals(0, map.getKey(2))
    Assertions.assertFalse(map.containsKey(2))
  }

  @Test
  fun `put force same value`() {
    map.put(1, 2)
    map.putForce(10, 2)

    assertEquals(2, map.get(10))
    assertEquals(10, map.getKey(2))
    assertTrue(map.containsValue(2))
    Assertions.assertFalse(map.containsKey(1))
  }
}