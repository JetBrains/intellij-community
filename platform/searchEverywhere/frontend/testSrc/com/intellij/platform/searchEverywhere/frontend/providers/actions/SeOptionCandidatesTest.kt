// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.ide.ui.search.OptionDescription
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SeOptionCandidatesTest {
  @Test
  fun `the option id holds the settings page and the hit`() {
    assertEquals("option:terminal|Show separators", optionCandidateId(OptionDescription(null, "terminal", "Show separators", null)))
  }

  @Test
  fun `an option without a settings page or a hit has no id`() {
    assertNull(optionCandidateId(OptionDescription(null, "terminal", null, null)))
    assertNull(optionCandidateId(OptionDescription(null, null, "Show separators", null)))
  }

  @Test
  fun `the selection takes the candidates with the most matched words first`() {
    val candidates = listOf("first", "second", "third", "fourth").map { option(it) }

    val selected = selectByMatchedWords(candidates, mapOf("second" to 1, "third" to 2, "fourth" to 1), limit = 2)

    assertEquals(listOf("third", "second"), selected.map { it.id })
  }

  @Test
  fun `the selection drops the candidates without matched words`() {
    val candidates = listOf("first", "second").map { option(it) }

    assertEquals(listOf("second"), selectByMatchedWords(candidates, mapOf("second" to 1), limit = 10).map { it.id })
  }

  private fun option(id: String): SeActionsRankerCandidate = SeActionsRankerCandidate(id, id, null, isOption = true)
}
