// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.collectionAssertion

import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertion.Companion.assertCollectionOrdered
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class CollectionAssertionTest {

  @Test
  fun `collection with matching elements passes`() {
    assertCollectionOrdered(listOf(1, 2, 3)) {
      assertElement { Assertions.assertEquals(1, it) }
      assertElement { Assertions.assertEquals(2, it) }
      assertElement { Assertions.assertEquals(3, it) }
    }
  }

  @Test
  fun `empty collection passes`() {
    assertCollectionOrdered(emptyList<Int>()) {}
  }

  @Test
  fun `fails when actual has more elements than expected`() {
    Assertions.assertThrows(AssertionError::class.java) {
      assertCollectionOrdered(listOf(1, 2, 3)) {
        assertElement { Assertions.assertEquals(1, it) }
      }
    }
  }

  @Test
  fun `fails when actual has fewer elements than expected`() {
    Assertions.assertThrows(AssertionError::class.java) {
      assertCollectionOrdered(listOf(1)) {
        assertElement { Assertions.assertEquals(1, it) }
        assertElement { Assertions.assertEquals(2, it) }
        assertElement { Assertions.assertEquals(3, it) }
      }
    }
  }

  @Test
  fun `failing element assertion reports index and preserves cause`() {
    val error = Assertions.assertThrows(AssertionError::class.java) {
      assertCollectionOrdered(listOf(10, 20, 30)) {
        assertElement { Assertions.assertEquals(10, it) }
        assertElement { Assertions.assertEquals(99, it) }
        assertElement { Assertions.assertEquals(30, it) }
      }
    }
    Assertions.assertTrue(error.message!!.contains("index=1"), "Expected error message to mention the failing index")
    Assertions.assertNotNull(error.cause, "Expected original assertion to be preserved as cause")
  }
}
