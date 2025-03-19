package com.intellij.cce.evaluable.conflictResolution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SplitTextTest {

  private val matrix1 = listOf(
    listOf(0, 1, 2, 3),
    listOf(1, 0, 1, 2),
    listOf(2, 1, 0, 1),
    listOf(3, 2, 1, 0),
  )

  private val matrix2 = listOf(
    listOf(0, 1, 2, 3, 4, 5),
    listOf(1, 2, 1, 2, 3, 4),
    listOf(2, 3, 2, 3, 4, 5),
    listOf(3, 4, 3, 4, 3, 4),
  )

  @Test
  fun `test editDistanceMatrix`() {
    assertEquals(matrix1, editDistance("abc", "abc"))
    assertEquals(matrix2, editDistance("abc", "_a_c_"))
  }

  @Test
  fun `test retrieveAlignment`() {
    assertEquals(Pair(listOf(0..2), listOf(0..2)), alignment(matrix1))
    assertEquals(Pair(listOf(0..0, 2..2), listOf(1..1, 3..3)), alignment(matrix2))
  }

  private fun editDistance(s1: String, s2: String) =
    calculateEditDistance(CharacterSplitText(s1), CharacterSplitText(s2)).map { it.toList() }

  private fun alignment(matrix: List<List<Int>>) =
    retrieveAlignment(matrix.map { it.toIntArray() }.toTypedArray())
}