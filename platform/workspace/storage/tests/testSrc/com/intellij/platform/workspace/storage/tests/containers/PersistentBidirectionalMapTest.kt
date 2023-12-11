// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests.containers

import com.intellij.platform.workspace.storage.impl.containers.PersistentBidirectionalMapImpl
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistentBidirectionalMapTest {
  @Test
  fun `test operations`() {
    val map = PersistentBidirectionalMapImpl<Int, Int>()

    val newMap = map.builder().also {
      it[1] = 1
      it[2] = 2
      it[3] = 3
    }.build()
    assertTrue(map.isEmpty())
    assertFalse(newMap.isEmpty())

    assertEquals(3, newMap.size)
    assertEquals(1, newMap[1])
    assertEquals(2, newMap[2])
    assertEquals(3, newMap[3])

    val third = newMap.builder().also {
      it.remove(1)
      it.remove(10)
    }.build()

    assertEquals(2, third.size)
    assertNull(third[1])
    assertEquals(2, third[2])
    assertEquals(3, third[3])

    assertNull(third.getKeysByValue(1))
    assertNull(third.getKeysByValue(10))
    assertEquals(2, third.getKeysByValue(2)!!.single())
    assertEquals(3, third.getKeysByValue(3)!!.single())

    val emptyMap = third.builder().also { it.clear() }.build()
    assertTrue(emptyMap.isEmpty())
  }
}
