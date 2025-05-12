// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SyntaxElementTypeSetTest {

  @Test
  fun `test sum of sets with the same element`() {
    val item1 = SyntaxElementType("item1")
    val item2 = SyntaxElementType("item2") // duplicated in both sets
    val item3 = SyntaxElementType("item3")

    val set1 = syntaxElementTypeSetOf(item1, item2)
    val set2 = syntaxElementTypeSetOf(item2, item3)

    val sum = set1 + set2

    assertEquals(3, sum.size)

    val iterated = mutableSetOf<SyntaxElementType>()
    for (elementType in sum) {
      assertTrue(iterated.add(elementType), "Duplicate element type: $elementType")
    }
  }

  @Test
  fun `test empty set operations`() {
    val emptySet = emptySyntaxElementTypeSet()
    assertTrue(emptySet.isEmpty())
    assertEquals(0, emptySet.size)
  }

  @Test
  fun `test contains and containsAll`() {
    val item1 = SyntaxElementType("item1")
    val item2 = SyntaxElementType("item2")
    val set = syntaxElementTypeSetOf(item1, item2)

    assertTrue(set.contains(item1))
    assertTrue(set.contains(item2))
    assertTrue(set.containsAll(listOf(item1, item2)))
  }

  @Test
  fun `test plus single element`() {
    val item1 = SyntaxElementType("item1")
    val item2 = SyntaxElementType("item2")
    val set = syntaxElementTypeSetOf(item1)

    val newSet = set + item2
    assertEquals(2, newSet.size)
    assertTrue(newSet.contains(item1))
    assertTrue(newSet.contains(item2))
  }

  @Test
  fun `test isEmpty`() {
    val emptySet = emptySyntaxElementTypeSet()
    assertTrue(emptySet.isEmpty())

    val nonEmptySet = syntaxElementTypeSetOf(SyntaxElementType("test"))
    assertTrue(!nonEmptySet.isEmpty())
  }

  @Test
  fun `test null element handling`() {
    val set = syntaxElementTypeSetOf(SyntaxElementType("test"))
    assertTrue(!set.contains(null))
  }

  @Test
  fun `test contains with duplicate elements`() {
    val item = SyntaxElementType("duplicate")
    val set = syntaxElementTypeSetOf(item, item)

    assertEquals(1, set.size)
    assertTrue(set.contains(item))
  }

  @Test
  fun `test contains with multiple checks`() {
    val item = SyntaxElementType("test")
    val set = syntaxElementTypeSetOf(item)

    assertTrue(set.contains(item))
    assertTrue(set.contains(item))
    assertTrue(set.contains(item))
  }

  @Test
  fun `test contains with non-existent element`() {
    val item = SyntaxElementType("existing")
    val nonExistent = SyntaxElementType("non-existent")
    val set = syntaxElementTypeSetOf(item)

    assertTrue(!set.contains(nonExistent))
  }

  @Test
  fun `test plus empty iterable`() {
    val item = SyntaxElementType("test")
    val set = syntaxElementTypeSetOf(item)
    val result = set + emptyList()

    assertEquals(1, result.size)
    assertTrue(result.contains(item))
  }

  @Test
  fun `test plus single element iterable`() {
    val item1 = SyntaxElementType("item1")
    val item2 = SyntaxElementType("item2")
    val set = syntaxElementTypeSetOf(item1)
    val result = set + listOf(item2)

    assertEquals(2, result.size)
    assertTrue(result.contains(item1))
    assertTrue(result.contains(item2))
  }

  @Test
  fun `test plus multiple sets`() {
    val item1 = SyntaxElementType("item1")
    val item2 = SyntaxElementType("item2")
    val item3 = SyntaxElementType("item3")

    val set1 = syntaxElementTypeSetOf(item1)
    val set2 = syntaxElementTypeSetOf(item2)
    val set3 = syntaxElementTypeSetOf(item3)

    val result = set1 + set2 + set3

    assertEquals(3, result.size)
    assertTrue(result.containsAll(listOf(item1, item2, item3)))
  }

  @Test
  fun `test plus with duplicate elements in iterable`() {
    val item1 = SyntaxElementType("item1")
    val item2 = SyntaxElementType("item2")
    val set = syntaxElementTypeSetOf(item1)
    val result = set + listOf(item2, item2, item2)

    assertEquals(2, result.size)
    assertTrue(result.contains(item1))
    assertTrue(result.contains(item2))
  }

  @Test
  fun `test plus with empty set`() {
    val item = SyntaxElementType("test")
    val set = syntaxElementTypeSetOf(item)
    val emptySet = emptySyntaxElementTypeSet()

    val result = set + emptySet
    assertEquals(1, result.size)
    assertTrue(result.contains(item))
  }

  @Test
  fun `test plus with multiple new elements`() {
    val initial = SyntaxElementType("initial")
    val new1 = SyntaxElementType("new1")
    val new2 = SyntaxElementType("new2")
    val new3 = SyntaxElementType("new3")

    val set = syntaxElementTypeSetOf(initial)
    val result = set + new1 + new2 + new3

    assertEquals(4, result.size)
    assertTrue(result.containsAll(listOf(initial, new1, new2, new3)))
  }

  @Test
  fun `test plus with same element`() {
    val initial = SyntaxElementType("initial")

    val set = syntaxElementTypeSetOf(initial)
    val result = set + initial

    assertEquals(1, result.size)
  }

  @Test
  fun `test plus with null element handling`() {
    val item = SyntaxElementType("test")
    val set = syntaxElementTypeSetOf(item)
    val nullList: List<SyntaxElementType?> = listOf(null)

    val result = set + nullList // not a SyntaxElementTypeSet!!!

    assertTrue(result !is SyntaxElementTypeSet)
    assertEquals(2, result.size)
    assertTrue(result.contains(item))
    assertTrue(result.contains(null))
  }

  @Test
  fun foo() {
    val array = arrayOf(1, 2, 3)
    val set = setOf(*array, 4)
    assertEquals(4, set.size)
  }
}