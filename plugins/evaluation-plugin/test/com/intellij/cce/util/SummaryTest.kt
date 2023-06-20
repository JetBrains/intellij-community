// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class SummaryTest {
  @Test
  fun `test empty`() {
    assertTrue { summary { }.isEmpty() }
  }

  @Test
  fun `test trivial values`() {
    val result = summary { inc("test") }
    assertEquals(1, result.size)
    assertEquals(1, result["test"])
  }

  @Test
  fun `test group values`() {
    val summary = summary { group("name") { inc("key") } }
    assertEquals(1, summary.size)
    val nested = summary["name"] as? Map<*, *>
    assertNotNull(nested)
    assert(nested?.get("key") == 1)
  }

  @Test
  fun `test throw if keys collision`() {
    summary {
      inc("key1")
      assertThrows<IllegalArgumentException> { group("key1") {} }
      group("key2") {
        inc("key3")
      }
      assertThrows<IllegalArgumentException> { inc("key2") }
    }
  }

  @Test
  fun `test counting summary limit`() {
    val result = summary {
      countingGroup("counting", 10) {
        for (i in 0..50) {
          for (j in 0..i) {
            inc("value$i")
          }
        }
      }
    }["counting"] as Map<*, *>

    assertEquals(12, result.size)
    assertTrue("WARNING" in result.keys)
    for (i in 41..50) {
      assertTrue("value$i" in result.keys)
    }
  }

  @Test
  fun `test counting group saves usual ones`() {
    val result = summary {
      countingGroup("counting", 0) {
        group("nested") {
          inc("value")
        }
      }
    }["counting"] as Map<*, *>
    assertEquals(2, result.size)
    assertTrue("nested" in result.keys)
  }

  @Test
  fun `test impossible to use usual group as counting`() {
    summary {
      group("usual") {
        inc("value")
      }
      assertThrows<IllegalArgumentException> { countingGroup("usual", 10) {} }
    }
  }

  @Test
  fun `test impossible to change counting limit`() {
    summary {
      countingGroup("counting", 5) {}
      assertThrows<IllegalArgumentException> { countingGroup("counting", 10) {} }
    }
  }

  @Test
  fun `test use counting group as regular one`() {
    summary {
      countingGroup("counting", 5) {}
      assertDoesNotThrow { group("counting") { inc("value") } }
    }
  }

  private fun summary(init: Summary.() -> Unit): Map<String, Any> {
    val summary = Summary.create()
    summary.init()
    return summary.asSerializable()
  }
}