// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.tests.containers

import com.intellij.platform.workspace.storage.impl.containers.BidirectionalMap
import com.intellij.testFramework.UsefulTestCase.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BidirectionalMapTest {
  private lateinit var bidirectionalMap: BidirectionalMap<Int, Int>

  @BeforeEach
  fun setUp() {
    bidirectionalMap = BidirectionalMap()
  }

  @Test
  fun `store unique values and make a copy of collection`() {
    bidirectionalMap[1] = 1
    bidirectionalMap[2] = 2
    bidirectionalMap[3] = 3
    assertEmpty(bidirectionalMap.getSlotsWithList())
    bidirectionalMap[2] = 2
    assertEmpty(bidirectionalMap.getSlotsWithList())
    bidirectionalMap.removeValue(2)
    bidirectionalMap.remove(1)
    val copy = bidirectionalMap.copy()
    Assertions.assertNotSame(copy.getSlotsWithList(), bidirectionalMap.getSlotsWithList())
    assertEmpty(copy.getSlotsWithList())
    assertNull(copy[1])
    assertNull(copy[2])
    assertNotNull(copy[3])
  }

  @Test
  fun `store not unique values and make a copy of collection`() {
    bidirectionalMap[1] = 1
    bidirectionalMap[2] = 2
    bidirectionalMap[3] = 2
    bidirectionalMap[4] = 4
    assertContainsElements(bidirectionalMap.getSlotsWithList(), 2)
    assertDoesntContain(bidirectionalMap.getSlotsWithList(), 4)
    bidirectionalMap[5] = 4

    assertContainsElements(bidirectionalMap.getKeysByValue(2)!!, 2, 3)
    assertContainsElements(bidirectionalMap.keys, 1, 2, 3, 4, 5)
    assertContainsElements(bidirectionalMap.values, 1, 2, 4)
    assertContainsElements(bidirectionalMap.getSlotsWithList(), 2, 4)

    bidirectionalMap.remove(3)
    assertContainsElements(bidirectionalMap.getKeysByValue(2)!!, 2)
    assertContainsElements(bidirectionalMap.keys, 1, 2, 4, 5)
    assertContainsElements(bidirectionalMap.values, 1, 2, 4)
    assertContainsElements(bidirectionalMap.getSlotsWithList(), 4)
    assertDoesntContain(bidirectionalMap.getSlotsWithList(), 2)

    bidirectionalMap.removeValue(4)
    assertContainsElements(bidirectionalMap.keys, 1, 2)
    assertContainsElements(bidirectionalMap.values, 1, 2)
    assertEmpty(bidirectionalMap.getSlotsWithList())

    bidirectionalMap[2] = 1
    bidirectionalMap[3] = 3
    bidirectionalMap[4] = 4
    assertContainsElements(bidirectionalMap.keys, 1, 2, 3, 4)
    assertContainsElements(bidirectionalMap.values, 1, 3, 4)
    assertContainsElements(bidirectionalMap.getSlotsWithList(), 1)

    bidirectionalMap[4] = 3
    assertContainsElements(bidirectionalMap.keys, 1, 2, 3, 4)
    assertContainsElements(bidirectionalMap.values, 1, 3)
    assertContainsElements(bidirectionalMap.getSlotsWithList(), 1, 3)

    bidirectionalMap[4] = 4
    assertContainsElements(bidirectionalMap.getSlotsWithList(), 1)
    assertDoesntContain(bidirectionalMap.getSlotsWithList(), 3)
    val copy = bidirectionalMap.copy()
    copy.assertConsistency()
    Assertions.assertNotSame(copy.getSlotsWithList(), bidirectionalMap.getSlotsWithList())
    assertContainsElements(copy.getSlotsWithList(), 1)
  }
}