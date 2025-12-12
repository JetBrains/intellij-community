// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResolutionMapTest {
  private data class TestElement(val id: String, val keys: List<String>)

  @Test
  fun `add elements with unique keys`() {
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { _, _, _ -> error("Should not be called") }
    )

    val e1 = TestElement("e1", listOf("k1", "k2"))
    val e2 = TestElement("e2", listOf("k3", "k4"))

    assertTrue(builder.add(e1))
    assertTrue(builder.add(e2))

    val map = builder.build()
    assertEquals(4, map.size)
    assertEquals(e1, map["k1"])
    assertEquals(e1, map["k2"])
    assertEquals(e2, map["k3"])
    assertEquals(e2, map["k4"])
  }

  @Test
  fun `add element with single key`() {
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { _, _, _ -> error("Should not be called") }
    )

    val e1 = TestElement("e1", listOf("k1"))
    assertTrue(builder.add(e1))

    val map = builder.build()
    assertEquals(1, map.size)
    assertEquals(e1, map["k1"])
  }

  @Test
  fun `conflict resolved in favor of existing element`() {
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { existing, _, _ -> existing }
    )

    val e1 = TestElement("e1", listOf("k1", "k2"))
    val e2 = TestElement("e2", listOf("k2", "k3"))

    assertTrue(builder.add(e1))
    assertFalse(builder.add(e2))

    val map = builder.build()
    assertEquals(2, map.size)
    assertEquals(e1, map["k1"])
    assertEquals(e1, map["k2"])
    assertEquals(null, map["k3"])
  }

  @Test
  fun `conflict resolved in favor of candidate element`() {
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { _, candidate, _ -> candidate }
    )

    val e1 = TestElement("e1", listOf("k1", "k2"))
    val e2 = TestElement("e2", listOf("k2", "k3"))

    assertTrue(builder.add(e1))
    assertTrue(builder.add(e2))

    val map = builder.build()
    assertEquals(2, map.size)
    assertEquals(null, map["k1"])
    assertEquals(e2, map["k2"])
    assertEquals(e2, map["k3"])
  }

  @Test
  fun `conflict resolved by removing both elements`() {
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { _, _, _ -> null }
    )

    val e1 = TestElement("e1", listOf("k1", "k2"))
    val e2 = TestElement("e2", listOf("k2", "k3"))

    assertTrue(builder.add(e1))
    assertFalse(builder.add(e2))

    val map = builder.build()
    assertEquals(0, map.size)
  }

  @Test
  fun `partial key registration before conflict`() {
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { existing, _, _ -> existing }
    )

    val e1 = TestElement("e1", listOf("k1"))
    val e2 = TestElement("e2", listOf("k2", "k3", "k1", "k4"))

    assertTrue(builder.add(e1))
    assertFalse(builder.add(e2))

    val map = builder.build()
    assertEquals(1, map.size)
    assertEquals(e1, map["k1"])
    assertEquals(null, map["k2"])
    assertEquals(null, map["k3"])
    assertEquals(null, map["k4"])
  }

  @Test
  fun `multiple conflicts with same element`() {
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { _, candidate, _ -> candidate }
    )

    val e1 = TestElement("e1", listOf("k1"))
    val e2 = TestElement("e2", listOf("k2"))
    val e3 = TestElement("e3", listOf("k1", "k2", "k3"))

    assertTrue(builder.add(e1))
    assertTrue(builder.add(e2))
    assertTrue(builder.add(e3))

    val map = builder.build()
    assertEquals(3, map.size)
    assertEquals(e3, map["k1"])
    assertEquals(e3, map["k2"])
    assertEquals(e3, map["k3"])
  }

  @Test
  fun `remove element successfully`() {
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { _, _, _ -> error("Should not be called") }
    )

    val e1 = TestElement("e1", listOf("k1", "k2"))
    val e2 = TestElement("e2", listOf("k3"))

    assertTrue(builder.add(e1))
    assertTrue(builder.add(e2))
    assertTrue(builder.remove(e1))

    val map = builder.build()
    assertEquals(1, map.size)
    assertEquals(null, map["k1"])
    assertEquals(null, map["k2"])
    assertEquals(e2, map["k3"])
  }

  @Test
  fun `remove non-existent element returns false`() {
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { _, _, _ -> error("Should not be called") }
    )

    val e1 = TestElement("e1", listOf("k1", "k2"))
    val e2 = TestElement("e2", listOf("k3"))

    assertTrue(builder.add(e1))
    assertFalse(builder.remove(e2))

    val map = builder.build()
    assertEquals(2, map.size)
    assertEquals(e1, map["k1"])
    assertEquals(e1, map["k2"])
  }

  @Test
  fun `remove element with single key`() {
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { _, _, _ -> error("Should not be called") }
    )

    val e1 = TestElement("e1", listOf("k1"))
    assertTrue(builder.add(e1))
    assertTrue(builder.remove(e1))

    val map = builder.build()
    assertEquals(0, map.size)
  }

  @Test
  fun `conflict resolver returns unexpected element throws`() {
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { _, _, _ -> TestElement("unexpected", listOf("x")) }
    )

    val e1 = TestElement("e1", listOf("k1"))
    val e2 = TestElement("e2", listOf("k1"))

    assertTrue(builder.add(e1))
    
    assertThrows<IllegalStateException> {
      builder.add(e2)
    }
  }

  @Test
  fun `order matters when conflicts exist`() {
    val builder1 = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { existing, _, _ -> existing }
    )
    
    val builder2 = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { existing, _, _ -> existing }
    )

    val e1 = TestElement("e1", listOf("k1"))
    val e2 = TestElement("e2", listOf("k1"))

    // Add in order e1, e2
    builder1.add(e1)
    builder1.add(e2)
    
    // Add in order e2, e1
    builder2.add(e2)
    builder2.add(e1)

    val map1 = builder1.build()
    val map2 = builder2.build()
    
    // Both resolve to the first added element (existing wins)
    assertEquals(e1, map1["k1"])
    assertEquals(e2, map2["k1"])
  }

  @Test
  fun `empty builder produces empty map`() {
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { _, _, _ -> error("Should not be called") }
    )

    val map = builder.build()
    assertEquals(0, map.size)
  }

  @Test
  fun `build can be called multiple times`() {
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { _, _, _ -> error("Should not be called") }
    )

    val e1 = TestElement("e1", listOf("k1"))
    assertTrue(builder.add(e1))

    val map1 = builder.build()
    val map2 = builder.build()
    
    assertEquals(1, map1.size)
    assertEquals(1, map2.size)
    assertEquals(e1, map1["k1"])
    assertEquals(e1, map2["k1"])
  }

  @Test
  fun `conflict at first key of candidate`() {
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { _, candidate, _ -> candidate }
    )

    val e1 = TestElement("e1", listOf("k1"))
    val e2 = TestElement("e2", listOf("k1", "k2", "k3"))

    assertTrue(builder.add(e1))
    assertTrue(builder.add(e2))

    val map = builder.build()
    assertEquals(3, map.size)
    assertEquals(e2, map["k1"])
    assertEquals(e2, map["k2"])
    assertEquals(e2, map["k3"])
  }

  @Test
  fun `conflict at middle key of candidate`() {
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { existing, _, _ -> existing }
    )

    val e1 = TestElement("e1", listOf("k2"))
    val e2 = TestElement("e2", listOf("k1", "k2", "k3"))

    assertTrue(builder.add(e1))
    assertFalse(builder.add(e2))

    val map = builder.build()
    assertEquals(1, map.size)
    assertEquals(null, map["k1"])
    assertEquals(e1, map["k2"])
    assertEquals(null, map["k3"])
  }

  @Test
  fun `conflict at last key of candidate`() {
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { existing, _, _ -> existing }
    )

    val e1 = TestElement("e1", listOf("k3"))
    val e2 = TestElement("e2", listOf("k1", "k2", "k3"))

    assertTrue(builder.add(e1))
    assertFalse(builder.add(e2))

    val map = builder.build()
    assertEquals(1, map.size)
    assertEquals(null, map["k1"])
    assertEquals(null, map["k2"])
    assertEquals(e1, map["k3"])
  }

  @Test
  fun `complex scenario with multiple adds and removes`() {
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { _, candidate, _ -> candidate }
    )

    val e1 = TestElement("e1", listOf("k1", "k2"))
    val e2 = TestElement("e2", listOf("k3", "k4"))
    val e3 = TestElement("e3", listOf("k2", "k5"))
    val e4 = TestElement("e4", listOf("k6"))

    assertTrue(builder.add(e1))
    assertTrue(builder.add(e2))
    assertTrue(builder.add(e3)) // e3 wins over e1 for k2
    assertTrue(builder.add(e4))
    
    assertTrue(builder.remove(e3))

    val map = builder.build()
    assertEquals(3, map.size)
    assertEquals(null, map["k1"])
    assertEquals(null, map["k2"])
    assertEquals(e2, map["k3"])
    assertEquals(e2, map["k4"])
    assertEquals(null, map["k5"])
    assertEquals(e4, map["k6"])
  }

  @Test
  fun `conflict resolution with key parameter is correct`() {
    val conflictLog = mutableListOf<Triple<String, String, String>>()
    
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { existing, candidate, key ->
        conflictLog.add(Triple(existing.id, candidate.id, key))
        existing
      }
    )

    val e1 = TestElement("e1", listOf("k1", "k2"))
    val e2 = TestElement("e2", listOf("k2", "k3"))

    assertTrue(builder.add(e1))
    assertFalse(builder.add(e2))

    assertEquals(1, conflictLog.size)
    assertEquals("e1", conflictLog[0].first)
    assertEquals("e2", conflictLog[0].second)
    assertEquals("k2", conflictLog[0].third)
  }

  @Test
  fun `element with duplicate keys is rejected`() {
    val conflictLog = mutableListOf<Triple<String, String, String>>()
    
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { existing, candidate, key ->
        conflictLog.add(Triple(existing.id, candidate.id, key))
        // Contract: must return null for self-conflict (duplicate keys)
        if (existing === candidate) null else existing
      }
    )

    val e1 = TestElement("e1", listOf("k1", "k2", "k1", "k3"))
    assertFalse(builder.add(e1))

    // The second k1 should trigger a self-conflict
    assertEquals(1, conflictLog.size)
    assertEquals("e1", conflictLog[0].first)
    assertEquals("e1", conflictLog[0].second)
    assertEquals("k1", conflictLog[0].third)

    val map = builder.build()
    // Element with duplicate keys is rejected entirely
    assertEquals(0, map.size)
  }

  @Test
  fun `element with duplicate keys throws if conflict resolver violates contract`() {
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { existing, _, _ -> existing } // Violates contract for self-conflict
    )

    val e1 = TestElement("e1", listOf("k1", "k1"))
    
    val exception = assertThrows<IllegalStateException> {
      builder.add(e1)
    }
    assertTrue(exception.message!!.contains("resolveConflict must return null when existing === candidate"))
  }

  @Test
  fun `complex scenario with duplicate keys and multiple elements`() {
    val conflictLog = mutableListOf<Triple<String, String, String>>()
    
    val builder = ResolutionMapBuilder<String, TestElement>(
      getKeys = { it.keys.asSequence() },
      resolveConflict = { existing, candidate, key ->
        conflictLog.add(Triple(existing.id, candidate.id, key))
        // Contract: must return null for self-conflict (duplicate keys)
        if (existing === candidate) {
          null
        } else {
          // For regular conflicts, prefer the candidate
          candidate
        }
      }
    )

    // Add a valid element first
    val e1 = TestElement("e1", listOf("k1", "k2"))
    assertTrue(builder.add(e1))

    // Try to add element with duplicate keys that also conflicts with e1
    val e2 = TestElement("e2", listOf("o", "k2", "k3", "k3", "k4"))
    assertFalse(builder.add(e2))

    // e2 should first register k2, causing conflict with e1 (e2 wins)
    // Then e2 should register k3
    // Then the second k3 should cause self-conflict (e2 rejected)
    // This means e1 should have been removed, but e2 was never fully added
    assertEquals(2, conflictLog.size)
    // First conflict: e1 vs e2 on k2
    assertEquals("e1", conflictLog[0].first)
    assertEquals("e2", conflictLog[0].second)
    assertEquals("k2", conflictLog[0].third)
    // Second conflict: e2 vs e2 on k3 (self-conflict)
    assertEquals("e2", conflictLog[1].first)
    assertEquals("e2", conflictLog[1].second)
    assertEquals("k3", conflictLog[1].third)

    val map = builder.build()
    // Both e1 and e2 should be removed
    assertEquals(0, map.size)

    // Add another valid element to verify builder is still functional
    val e3 = TestElement("e3", listOf("k5", "k6"))
    assertTrue(builder.add(e3))
    
    val finalMap = builder.build()
    assertEquals(2, finalMap.size)
    assertEquals(e3, finalMap["k5"])
    assertEquals(e3, finalMap["k6"])
  }
}