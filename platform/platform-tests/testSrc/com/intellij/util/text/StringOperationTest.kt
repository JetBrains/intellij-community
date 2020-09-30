// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("SpellCheckingInspection")

package com.intellij.util.text

import com.intellij.util.text.StringOperation.*
import groovy.transform.CompileStatic
import org.junit.Assert.assertEquals
import org.junit.Test

@CompileStatic
class StringOperationTest {

  @Test
  fun `operations equivalence`() {
    assertEquals(replace(1, 1, "x"), insert(1, "x"))
    assertEquals(replace(1, 2, ""), remove(1, 2))
  }

  @Test
  fun `no operations`() {
    assertEquals("", applyOperations("", emptyList()))
    assertEquals("abc", applyOperations("abc", emptyList()))
  }

  @Test
  fun replace() {
    assertEquals("ac", applyOperations("abc", listOf(replace(1, 2, ""))))
    assertEquals("adc", applyOperations("abc", listOf(replace(1, 2, "d"))))
    assertEquals("abc", applyOperations("abc", listOf(replace(1, 1, ""))))
    assertEquals("adbc", applyOperations("abc", listOf(replace(1, 1, "d"))))
  }

  @Test
  fun `replace whole string`() {
    assertEquals("", applyOperations("", listOf(replace(0, 0, ""))))
    assertEquals("a", applyOperations("", listOf(replace(0, 0, "a"))))
    assertEquals("", applyOperations("abc", listOf(replace(0, 3, ""))))
    assertEquals("d", applyOperations("abc", listOf(replace(0, 3, "d"))))
  }

  @Test
  fun `replace ends`() {
    assertEquals("abc", applyOperations("abc", listOf(replace(0, 0, ""))))
    assertEquals("dabc", applyOperations("abc", listOf(replace(0, 0, "d"))))
    assertEquals("abc", applyOperations("abc", listOf(replace(3, 3, ""))))
    assertEquals("abcd", applyOperations("abc", listOf(replace(3, 3, "d"))))
  }

  @Test(expected = IndexOutOfBoundsException::class)
  fun `replace outside of string`() {
    applyOperations("abc", listOf(replace(2, 4, "d")))
  }

  @Test(expected = IndexOutOfBoundsException::class)
  fun `insert outside of string`() {
    applyOperations("abc", listOf(replace(4, 4, "d")))
  }

  @Test(expected = IndexOutOfBoundsException::class)
  fun `remove outside of string`() {
    applyOperations("abc", listOf(replace(2, 4, "")))
  }

  @Test
  fun `insert near remove`() {
    assertEquals("adc", applyOperations("abc", listOf(insert(1, "d"), remove(1, 2))))
    assertEquals("adc", applyOperations("abc", listOf(remove(1, 2), insert(2, "d"))))
    assertEquals("adec", applyOperations("abc", listOf(insert(1, "d"), remove(1, 2), insert(2, "e"))))
  }

  @Test
  fun `replace same empty range twice`() {
    assertEquals("", applyOperations("", listOf(replace(0, 0, ""), replace(0, 0, ""))))
    assertEquals("abc", applyOperations("abc", listOf(replace(1, 1, ""), replace(1, 1, ""))))
    assertEquals("adbc", applyOperations("abc", listOf(replace(1, 1, "d"), replace(1, 1, ""))))
    assertEquals("adbc", applyOperations("abc", listOf(replace(1, 1, ""), replace(1, 1, "d"))))
    assertEquals("adebc", applyOperations("abc", listOf(replace(1, 1, "d"), replace(1, 1, "e"))))
  }

  @Test(expected = IndexOutOfBoundsException::class)
  fun `remove same range twice`() {
    applyOperations("abc", listOf(replace(1, 2, ""), replace(1, 2, "")))
  }

  @Test(expected = IndexOutOfBoundsException::class)
  fun `replace same range twice`() {
    applyOperations("abc", listOf(replace(1, 2, "d"), replace(1, 2, "e")))
  }

  @Test(expected = IndexOutOfBoundsException::class)
  fun `remove intersected ranges`() {
    applyOperations("abc", listOf(replace(0, 2, ""), replace(1, 3, "")))
  }

  @Test(expected = IndexOutOfBoundsException::class)
  fun `replace intersected ranges`() {
    applyOperations("abc", listOf(replace(0, 2, "d"), replace(1, 3, "e")))
  }

  @Test(expected = IndexOutOfBoundsException::class)
  fun `remove ranges with same start offset`() {
    applyOperations("abc", listOf(replace(1, 2, ""), replace(1, 3, "")))
  }

  @Test(expected = IndexOutOfBoundsException::class)
  fun `replace ranges with same start offset`() {
    applyOperations("abc", listOf(replace(1, 2, "d"), replace(1, 3, "e")))
  }

  @Test(expected = IndexOutOfBoundsException::class)
  fun `remove ranges with same end offset`() {
    applyOperations("abc", listOf(replace(0, 2, ""), replace(1, 2, "")))
  }

  @Test(expected = IndexOutOfBoundsException::class)
  fun `replace ranges with same end offset`() {
    applyOperations("abc", listOf(replace(0, 2, "d"), replace(1, 2, "e")))
  }

  @Test
  fun `unsorted operations`() {
    assertEquals("bxxxc", applyOperations("abcde", listOf(remove(3, 5), insert(2, "xxx"), remove(0, 1))))
  }
}
