// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.collectionAssertion

import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertContainsUnordered
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEmpty
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsOrdered
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class CollectionAssertionTest {

  @Test
  fun `test CollectionAssertions#assertEqualsOrdered`() {
    assertEqualsOrdered(listOf(1, 2, 3), listOf(1, 2, 3))
    assertEqualsOrdered(setOf(1, 2, 3), setOf(1, 2, 3))
    Assertions.assertThrows(AssertionError::class.java) {
      assertEqualsOrdered(listOf(1, 2, 3), listOf(3, 2, 1))
    }
    Assertions.assertThrows(AssertionError::class.java) {
      assertEqualsOrdered(setOf(1, 2, 3), setOf(3, 2, 1))
    }
    Assertions.assertThrows(AssertionError::class.java) {
      assertEqualsOrdered(listOf(1, 2, 2, 3), listOf(1, 2, 3))
    }
    assertEqualsOrdered(setOf(1, 2, 2, 3), setOf(1, 2, 3))
    Assertions.assertThrows(AssertionError::class.java) {
      assertEqualsOrdered(listOf(1, 2, 3), listOf(1, 2))
    }
    Assertions.assertThrows(AssertionError::class.java) {
      assertEqualsOrdered(setOf(1, 2, 3), setOf(1, 2))
    }
    Assertions.assertThrows(AssertionError::class.java) {
      assertEqualsOrdered(listOf(1, 2), listOf(1, 2, 3))
    }
    Assertions.assertThrows(AssertionError::class.java) {
      assertEqualsOrdered(setOf(1, 2), setOf(1, 2, 3))
    }
    assertEqualsOrdered(null as List<*>?, null)
    assertEqualsOrdered(null as Set<*>?, null)
    Assertions.assertThrows(AssertionError::class.java) {
      assertEqualsOrdered(listOf(1, 2, 3), null)
    }
    Assertions.assertThrows(AssertionError::class.java) {
      assertEqualsOrdered(null, listOf(1, 2, 3))
    }
  }

  @Test
  fun `test CollectionAssertions#assertEqualsUnordered`() {
    assertEqualsUnordered(listOf(1, 2, 3), listOf(3, 2, 1))
    assertEqualsUnordered(setOf(1, 2, 3), setOf(3, 2, 1))
    assertEqualsUnordered(listOf(1, 2, 2, 3), listOf(3, 2, 1))
    assertEqualsUnordered(setOf(1, 2, 2, 3), setOf(3, 2, 1))
    Assertions.assertThrows(AssertionError::class.java) {
      assertEqualsUnordered(listOf(1, 2, 3), listOf(1, 2))
    }
    Assertions.assertThrows(AssertionError::class.java) {
      assertEqualsUnordered(setOf(1, 2, 3), setOf(1, 2))
    }
    Assertions.assertThrows(AssertionError::class.java) {
      assertEqualsUnordered(listOf(1, 2), listOf(3, 2, 1))
    }
    Assertions.assertThrows(AssertionError::class.java) {
      assertEqualsUnordered(setOf(1, 2), setOf(1, 2, 3))
    }
    assertEqualsUnordered<Nothing?>(null, null)
    Assertions.assertThrows(AssertionError::class.java) {
      assertEqualsUnordered(listOf(1, 2, 3), null)
    }
    Assertions.assertThrows(AssertionError::class.java) {
      assertEqualsUnordered(null, listOf(1, 2, 3))
    }
  }

  @Test
  fun `test CollectionAssertions#assertContains`() {
    assertContainsUnordered(listOf(1, 2, 3), listOf(3, 2, 1))
    assertContainsUnordered(setOf(1, 2, 3), setOf(3, 2, 1))
    assertContainsUnordered(listOf(1, 2), listOf(3, 2, 1))
    assertContainsUnordered(setOf(1, 2), setOf(1, 2, 3))
    assertContainsUnordered(listOf(1, 2, 2), listOf(3, 2, 1))
    assertContainsUnordered(setOf(1, 2, 2), setOf(3, 2, 1))
    Assertions.assertThrows(AssertionError::class.java) {
      assertContainsUnordered(listOf(1, 2, 3), listOf(1, 2))
    }
    Assertions.assertThrows(AssertionError::class.java) {
      assertContainsUnordered(setOf(1, 2, 3), setOf(1, 2))
    }
  }

  @Test
  fun `test CollectionAssertions#assertEmpty`() {
    assertEmpty(emptyList<String>())
    assertEmpty(emptySet<String>())
    Assertions.assertThrows(AssertionError::class.java) {
      assertEmpty(listOf(1, 2, 3))
    }
    Assertions.assertThrows(AssertionError::class.java) {
      assertEmpty(setOf(1, 2, 3))
    }
  }
}