// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.vcs.ex.mergeSortedClientIds
import org.junit.Assert.assertEquals
import org.junit.Test

class LineStatusClientIdTrackerTest {
  @Test
  fun testMergeSortedClientIds() {
    checkSortedMerge(listOf("1"), listOf("2", "3"))

    val inputs = listOf(
      listOf(),
      listOf("1"),
      listOf("2"),
      listOf("3"),
      listOf("A2"),
      listOf("z"),
      listOf("1", "2"),
      listOf("1", "2", "3"),
      listOf("2", "3"),
      listOf("1", "3"),
      listOf("1", "3", "A2"),
      listOf("1", "2", "5", "7"),
      listOf("1", "4", "5", "7"),
      listOf("4", "7"),
      listOf("2", "7"),
      listOf("6", "7"),
      listOf("A2", "A3", "A7"),
      listOf("5", "A3", "A7"),
      listOf("4", "A3", "A7"),
      listOf("5", "A7"),
      listOf("5", "A8"),
    )

    for (input1 in inputs) {
      for (input2 in inputs) {
        checkSortedMerge(input1, input2)
      }
    }
  }

  private fun checkSortedMerge(clientIds1: List<String>, clientIds2: List<String>) {
    return checkMergeSortedClientIds(clientIds1.map { ClientId(it) }, clientIds2.map { ClientId(it) })
  }

  private fun checkMergeSortedClientIds(clientIds1: List<ClientId>, clientIds2: List<ClientId>) {
    assertEquals("Invalid test input", clientIds1.sortedBy { it.value }, clientIds1)
    assertEquals("Invalid test input", clientIds2.sortedBy { it.value }, clientIds2)

    val expected = naiveMergeSortedClientIds(clientIds1, clientIds2)
    val actual = mergeSortedClientIds(clientIds1, clientIds2)
    assertEquals("Input: {$clientIds1}, {$clientIds2}", expected, actual)
  }

  private fun naiveMergeSortedClientIds(clientIds1: List<ClientId>, clientIds2: List<ClientId>): List<ClientId> {
    val result = HashSet<ClientId>()
    result.addAll(clientIds1)
    result.addAll(clientIds2)
    return result.toList().sortedBy { it.value }
  }
}