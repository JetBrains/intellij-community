// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions

import com.intellij.platform.syntax.extensions.impl.ConcurrentMultiMap
import com.intellij.platform.syntax.extensions.impl.newConcurrentMultiMap
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiMapTest {
  private lateinit var multiMap: ConcurrentMultiMap<String, String>

  @BeforeEach
  fun setUp() {
    multiMap = newConcurrentMultiMap()
  }

  @Test
  fun testAddSingleValue() {
    multiMap.putValue("key1", "value1")
    assertContentEquals(listOf("value1"), multiMap.get("key1"))
  }

  @Test
  fun testAddMultipleValues() {
    multiMap.putValue("key1", "value1")
    multiMap.putValue("key1", "value2")
    assertEquals(setOf("value1", "value2"), multiMap.get("key1"))
  }

  @Test
  fun testGetValues() {
    multiMap.putValue("key1", "value1")
    multiMap.putValue("key1", "value2")
    multiMap.putValue("key2", "value3")

    assertEquals(setOf("value1", "value2"), multiMap.get("key1"))
    assertEquals(setOf("value3"), multiMap.get("key2"))
  }

  @Test
  fun testRemoveValue() {
    multiMap.putValue("key1", "value1")
    multiMap.putValue("key1", "value2")
    multiMap.remove("key1", "value1")

    assertEquals(setOf("value2"), multiMap.get("key1"))
  }

  @Test
  fun testGetEmptyCollection() {
    assertTrue(multiMap.get("non-existent").isEmpty())
  }
}
