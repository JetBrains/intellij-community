// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.collectionAssertion

import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertContainsUnordered
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEmpty
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsOrdered
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertNotEmpty
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertSingle
import com.intellij.testFramework.utils.collectionAssertion.CollectionAssertionsTest.CollectionType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.EnumSource
import java.util.LinkedList

@ParameterizedClass
@EnumSource(CollectionType::class)
class CollectionAssertionsTest(private val collectionType: CollectionType) {

  enum class CollectionType(
    val isOrdered: Boolean,
    val deduplicates: Boolean,
  ) {
    HASH_SET(false, true),
    LINKED_HASH_SET(true, true),
    ARRAY_LIST(true, false),
    LINKED_LIST(true, false),
    ARRAY_DEQUE(true, false)
  }

  private fun <T> collectionOf(vararg elements: T): Collection<T> = when (collectionType) {
    CollectionType.HASH_SET -> HashSet(elements.asList())
    CollectionType.LINKED_HASH_SET -> LinkedHashSet(elements.asList())
    CollectionType.ARRAY_LIST -> ArrayList(elements.asList())
    CollectionType.LINKED_LIST -> LinkedList(elements.asList())
    CollectionType.ARRAY_DEQUE -> ArrayDeque(elements.asList())
  }

  @Nested
  inner class AssertEqualsOrdered {

    @Test
    fun `passes for equal elements in same order`() {
      assertEqualsOrdered(collectionOf(1, 2, 3), collectionOf(1, 2, 3))
    }

    @Test
    fun `ordered collection fails for same elements in different order`() {
      Assumptions.assumeTrue(collectionType.isOrdered)
      Assertions.assertThrows(AssertionError::class.java) {
        assertEqualsOrdered(collectionOf(1, 2, 3), collectionOf(3, 2, 1))
      }
    }

    @Test
    fun `unordered collection passes for same elements in different insertion order`() {
      Assumptions.assumeFalse(collectionType.isOrdered)
      assertEqualsOrdered(collectionOf(1, 2, 3), collectionOf(3, 2, 1))
    }

    @Test
    fun `deduplicating collection passes when input has duplicates`() {
      Assumptions.assumeTrue(collectionType.deduplicates)
      assertEqualsOrdered(collectionOf(1, 2, 2, 3), collectionOf(1, 2, 3))
    }

    @Test
    fun `non-deduplicating collection fails when input has duplicates`() {
      Assumptions.assumeFalse(collectionType.deduplicates)
      Assertions.assertThrows(AssertionError::class.java) {
        assertEqualsOrdered(collectionOf(1, 2, 2, 3), collectionOf(1, 2, 3))
      }
    }

    @Test
    fun `fails when actual has fewer elements`() {
      Assertions.assertThrows(AssertionError::class.java) {
        assertEqualsOrdered(collectionOf(1, 2, 3), collectionOf(1, 2))
      }
    }

    @Test
    fun `fails when actual has more elements`() {
      Assertions.assertThrows(AssertionError::class.java) {
        assertEqualsOrdered(collectionOf(1, 2), collectionOf(1, 2, 3))
      }
    }

    @Test
    fun `passes when both are null`() {
      assertEqualsOrdered<Nothing?>(null, null)
    }

    @Test
    fun `fails when expected is not null but actual is null`() {
      Assertions.assertThrows(AssertionError::class.java) {
        assertEqualsOrdered(collectionOf(1, 2, 3), null)
      }
    }

    @Test
    fun `fails when expected is null but actual is not null`() {
      Assertions.assertThrows(AssertionError::class.java) {
        assertEqualsOrdered(null, collectionOf(1, 2, 3))
      }
    }
  }

  @Nested
  inner class AssertEqualsUnordered {

    @Test
    fun `passes regardless of order`() {
      assertEqualsUnordered(collectionOf(1, 2, 3), collectionOf(3, 2, 1))
    }

    @Test
    fun `passes when collection has duplicates`() {
      assertEqualsUnordered(collectionOf(1, 2, 2, 3), collectionOf(3, 2, 1))
    }

    @Test
    fun `fails when actual has fewer elements`() {
      Assertions.assertThrows(AssertionError::class.java) {
        assertEqualsUnordered(collectionOf(1, 2, 3), collectionOf(1, 2))
      }
    }

    @Test
    fun `fails when actual has more elements`() {
      Assertions.assertThrows(AssertionError::class.java) {
        assertEqualsUnordered(collectionOf(1, 2), collectionOf(3, 2, 1))
      }
    }

    @Test
    fun `passes when both are null`() {
      assertEqualsUnordered<Nothing?>(null, null)
    }

    @Test
    fun `fails when expected is not null but actual is null`() {
      Assertions.assertThrows(AssertionError::class.java) {
        assertEqualsUnordered(collectionOf(1, 2, 3), null)
      }
    }

    @Test
    fun `fails when expected is null but actual is not null`() {
      Assertions.assertThrows(AssertionError::class.java) {
        assertEqualsUnordered(null, collectionOf(1, 2, 3))
      }
    }
  }

  @Nested
  inner class AssertContainsUnordered {

    @Test
    fun `passes when actual equals expected`() {
      assertContainsUnordered(collectionOf(1, 2, 3), collectionOf(3, 2, 1))
    }

    @Test
    fun `passes when actual contains more elements than expected`() {
      assertContainsUnordered(collectionOf(1, 2), collectionOf(3, 2, 1))
    }

    @Test
    fun `passes when expected has duplicates`() {
      assertContainsUnordered(collectionOf(1, 2, 2), collectionOf(3, 2, 1))
    }

    @Test
    fun `fails when actual is missing expected elements`() {
      Assertions.assertThrows(AssertionError::class.java) {
        assertContainsUnordered(collectionOf(1, 2, 3), collectionOf(1, 2))
      }
    }
  }

  @Nested
  inner class AssertEmpty {

    @Test
    fun `passes for empty collection`() {
      assertEmpty(collectionOf<Int>())
    }

    @Test
    fun `fails for non-empty collection`() {
      Assertions.assertThrows(AssertionError::class.java) {
        assertEmpty(collectionOf(1, 2, 3))
      }
    }
  }

  @Nested
  inner class AssertNotEmpty {

    @Test
    fun `passes for collection with single element`() {
      assertNotEmpty(collectionOf(1))
    }

    @Test
    fun `passes for collection with multiple elements`() {
      assertNotEmpty(collectionOf(1, 2, 3))
    }

    @Test
    fun `fails for empty collection`() {
      Assertions.assertThrows(AssertionError::class.java) {
        assertNotEmpty(collectionOf<Int>())
      }
    }
  }

  @Nested
  inner class AssertSingle {

    @Test
    fun `passes for collection with expected single element`() {
      assertSingle(1, collectionOf(1))
    }

    @Test
    fun `deduplicating collection passes when input has duplicate expected element`() {
      Assumptions.assumeTrue(collectionType.deduplicates)
      assertSingle(1, collectionOf(1, 1))
    }

    @Test
    fun `fails for empty collection`() {
      Assertions.assertThrows(AssertionError::class.java) {
        assertSingle(1, collectionOf())
      }
    }

    @Test
    fun `fails when collection has different single element`() {
      Assertions.assertThrows(AssertionError::class.java) {
        assertSingle(1, collectionOf(2))
      }
    }

    @Test
    fun `fails when collection has more elements`() {
      Assertions.assertThrows(AssertionError::class.java) {
        assertSingle(1, collectionOf(1, 2))
      }
    }

    @Test
    fun `non-deduplicating collection fails when input has duplicate expected element`() {
      Assumptions.assumeFalse(collectionType.deduplicates)
      Assertions.assertThrows(AssertionError::class.java) {
        assertSingle(1, collectionOf(1, 1))
      }
    }
  }
}
