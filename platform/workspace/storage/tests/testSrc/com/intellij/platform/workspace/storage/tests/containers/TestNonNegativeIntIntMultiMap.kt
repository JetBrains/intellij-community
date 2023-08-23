// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.tests.containers

import com.intellij.platform.workspace.storage.impl.containers.MutableNonNegativeIntIntMultiMap
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestNonNegativeIntIntMultiMap {
  @Test
  internal fun `add and remove value`() {
    val multimap = MutableNonNegativeIntIntMultiMap.ByList()
    multimap.putAll(1, intArrayOf(2))
    assertEquals(2, multimap[1].single())
    multimap.remove(1)
    assertTrue(multimap[1].isEmpty())
  }

  @Test
  internal fun `add and remove multiple values`() {
    val multimap = MutableNonNegativeIntIntMultiMap.ByList()
    multimap.putAll(1, intArrayOf(2))
    multimap.putAll(1, intArrayOf(3))
    multimap.putAll(1, intArrayOf(4))
    Assertions.assertArrayEquals(intArrayOf(2, 3, 4), multimap[1].toArray())
    multimap.remove(1)
    assertTrue(multimap[1].isEmpty())
  }

  @Test
  internal fun `add and remove multiple values one by one`() {
    val multimap = MutableNonNegativeIntIntMultiMap.ByList()
    multimap.putAll(1, intArrayOf(2))
    multimap.putAll(1, intArrayOf(3))
    multimap.putAll(1, intArrayOf(4))
    Assertions.assertArrayEquals(intArrayOf(2, 3, 4), multimap[1].toArray())
    multimap.remove(1, 3)
    Assertions.assertArrayEquals(intArrayOf(2, 4), multimap[1].toArray())
    multimap.remove(1, 2)
    Assertions.assertArrayEquals(intArrayOf(4), multimap[1].toArray())
    multimap.remove(1, 4)
    assertTrue(multimap[1].isEmpty())
  }
}