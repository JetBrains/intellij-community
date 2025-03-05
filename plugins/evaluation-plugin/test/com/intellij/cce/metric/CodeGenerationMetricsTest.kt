// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.*
import com.intellij.cce.evaluable.AIA_HAS_SYNTAX_ERRORS
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class CodeGenerationMetricsTest {
  @Test
  fun `test correct results`() {
    doTest(sessions = listOf(
      session(singleSuggestion(result = "not-empty"), withSyntaxErrors = false),
      session(singleSuggestion(result = "not-empty-2"), withSyntaxErrors = false),
      session(singleSuggestion(result = "not-empty-3"), withSyntaxErrors = false),
    ), listOf(
      WithoutSyntaxErrorsSessionRatio() to 1.0,
      NotEmptyResultsRatio() to 1.0
    ))
  }

  @Test
  fun `test incorrect results`() {
    doTest(sessions = listOf(
      session(singleSuggestion(result = ""), withSyntaxErrors = true),
      session(singleSuggestion(result = ""), withSyntaxErrors = true),
      session(singleSuggestion(result = "with-errors"), withSyntaxErrors = true),
      session(singleSuggestion(result = "with-errors-2"), withSyntaxErrors = true),
    ), listOf(
      WithoutSyntaxErrorsSessionRatio() to 0.0,
      NotEmptyResultsRatio() to 0.5
    ))
  }

  @Test
  fun `test mixed results`() {
    doTest(sessions = listOf(
      session(singleSuggestion(result = ""), withSyntaxErrors = true),
      session(singleSuggestion(result = "not-empty"), withSyntaxErrors = false),
      session(singleSuggestion(result = "not-empty-2"), withSyntaxErrors = false),
      session(singleSuggestion(result = "with errors"), withSyntaxErrors = true),
    ), listOf(
      WithoutSyntaxErrorsSessionRatio() to 0.5,
      NotEmptyResultsRatio() to 0.75
    ))
  }

  @Test
  fun `test session without suggestions`() {
    doTest(sessions = listOf(
      session(emptyList(), withSyntaxErrors = true),
    ), listOf(
      WithoutSyntaxErrorsSessionRatio() to 0.0,
      NotEmptyResultsRatio() to 0.0
    ))
  }

  private fun doTest(sessions: List<Session>, metric2expected: List<Pair<Metric, Double>>) {
    for ((metric, expected) in metric2expected) {
      Assertions.assertEquals(expected, metric.evaluate(sessions))
    }
  }

  private fun singleSuggestion(result: String): List<Suggestion> = listOf(Suggestion(result, result, SuggestionSource.INTELLIJ))

  private fun session(suggestions: List<Suggestion>, withSyntaxErrors: Boolean): Session {
    val session = Session(0, "", 0, TokenProperties.UNKNOWN)
    session.addLookup(Lookup(
      prefix = "",
      offset = 0,
      suggestions = suggestions,
      latency = 0,
      features = null,
      selectedPosition = 0,
      isNew = true,
      additionalInfo = mapOf(AIA_HAS_SYNTAX_ERRORS to withSyntaxErrors)
    ))
    return session
  }
}
