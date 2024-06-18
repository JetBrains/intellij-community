// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class CodeGenerationMetricsTest {
  @Test
  fun `test correct results`() {
    doTest(sessions = listOf(
      session(result = "not-empty", withSyntaxErrors = false),
      session(result = "not-empty-2", withSyntaxErrors = false),
      session(result = "not-empty-3", withSyntaxErrors = false),
    ), listOf(
      WithoutSyntaxErrorsSessionRatio() to 1.0,
      NotEmptyResultsRatio() to 1.0
    ))
  }

  @Test
  fun `test incorrect results`() {
    doTest(sessions = listOf(
      session(result = "", withSyntaxErrors = true),
      session(result = "", withSyntaxErrors = true),
      session(result = "with-errors", withSyntaxErrors = true),
      session(result = "with-errors-2", withSyntaxErrors = true),
    ), listOf(
      WithoutSyntaxErrorsSessionRatio() to 0.0,
      NotEmptyResultsRatio() to 0.5
    ))
  }

  @Test
  fun `test mixed results`() {
    doTest(sessions = listOf(
      session(result = "", withSyntaxErrors = true),
      session(result = "not-empty", withSyntaxErrors = false),
      session(result = "not-empty-2", withSyntaxErrors = false),
      session(result = "with errors", withSyntaxErrors = true),
    ), listOf(
      WithoutSyntaxErrorsSessionRatio() to 0.5,
      NotEmptyResultsRatio() to 0.75
    ))
  }

  private fun doTest(sessions: List<Session>, metric2expected: List<Pair<Metric, Double>>) {
    for ((metric, expected) in metric2expected) {
      Assertions.assertEquals(expected, metric.evaluate(sessions))
    }
  }

  private fun session(result: String, withSyntaxErrors: Boolean): Session {
    val session = Session(0, "", 0, TokenProperties.UNKNOWN)
    session.addLookup(Lookup(
      prefix = "",
      offset = 0,
      suggestions = listOf(Suggestion(result, result, SuggestionSource.INTELLIJ)),
      latency = 0,
      features = null,
      selectedPosition = 0,
      isNew = true,
      additionalInfo = mapOf(HasSyntaxErrorsProperty to withSyntaxErrors)
    ))
    return session
  }
}

private const val HasSyntaxErrorsProperty = "has_syntax_errors"