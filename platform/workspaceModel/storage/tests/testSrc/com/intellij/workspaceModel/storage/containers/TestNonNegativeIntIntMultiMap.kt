// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.containers

import com.intellij.workspaceModel.storage.impl.containers.MutableNonNegativeIntIntMultiMap
import org.junit.Assert.*
import org.junit.Test

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
    assertArrayEquals(intArrayOf(2, 3, 4), multimap[1].toArray())
    multimap.remove(1)
    assertTrue(multimap[1].isEmpty())
  }

  @Test
  internal fun `add and remove multiple values one by one`() {
    val multimap = MutableNonNegativeIntIntMultiMap.ByList()
    multimap.putAll(1, intArrayOf(2))
    multimap.putAll(1, intArrayOf(3))
    multimap.putAll(1, intArrayOf(4))
    assertArrayEquals(intArrayOf(2, 3, 4), multimap[1].toArray())
    multimap.remove(1, 3)
    assertArrayEquals(intArrayOf(2, 4), multimap[1].toArray())
    multimap.remove(1, 2)
    assertArrayEquals(intArrayOf(4), multimap[1].toArray())
    multimap.remove(1, 4)
    assertTrue(multimap[1].isEmpty())
  }
}