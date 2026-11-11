// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@TestApplication
internal class ExecutorsQueryTest {
  @Test
  fun `executor throwing Error is logged and skipped`() {
    val error = NoClassDefFoundError("test")
    val query = ExecutorsQuery(Unit, listOf(
      good("a"),
      QueryExecutor<String, Unit> { _, _ -> throw error },
      good("b"),
    ))
    val results = ArrayList<String>()
    var completed = false
    val logged = LoggedErrorProcessor.executeAndReturnLoggedError {
      completed = query.forEach(Processor { results.add(it) })
    }
    assertSame(error, logged)
    assertTrue(completed)
    assertEquals(listOf("a", "b"), results)
  }

  @Test
  fun `executor throwing PCE stops query`() {
    val pce = ProcessCanceledException()
    val query = ExecutorsQuery(Unit, listOf(
      good("a"),
      QueryExecutor<String, Unit> { _, _ -> throw pce },
      good("b"),
    ))
    val results = ArrayList<String>()
    val thrown = assertThrows<ProcessCanceledException> {
      query.forEach(Processor { results.add(it) })
    }
    assertSame(pce, thrown)
    assertEquals(listOf("a"), results)
  }

  private fun good(value: String): QueryExecutor<String, Unit> = QueryExecutor { _, consumer -> consumer.process(value) }
}
