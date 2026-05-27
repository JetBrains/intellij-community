// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.model

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Rule
import org.junit.rules.Timeout

class AIReviewViewModelTest : BasePlatformTestCase() {
  @get:Rule
  val timeout: Timeout = Timeout.seconds(120)

  fun `test full review state exposes all problems`() {
    val request = AIReviewRequest.LocalChanges(changes = emptyList())
    val problem1 = problem(id = "1", message = "first")
    val problem2 = problem(id = "2", message = "second")
    val viewModel = AIReviewViewModel(project, request)

    viewModel.setFullReviewState(AIReviewResult(request, listOf(problem1, problem2)))

    assertEquals(listOf(problem1, problem2), viewModel.problems.value)
  }

  fun `test error state keeps partial review problems`() {
    val request = AIReviewRequest.LocalChanges(changes = emptyList())
    val problem = problem(id = "1", message = "partial")
    val viewModel = AIReviewViewModel(project, request)

    viewModel.setErrorState(IllegalStateException("failed"), AIReviewResult(request, listOf(problem)))

    assertEquals(listOf(problem), viewModel.problems.value)
  }

  fun `test review state deduplicates problems with different generated ids`() {
    val request = AIReviewRequest.LocalChanges(changes = emptyList())
    val problem = problem(id = "1", message = "duplicate")
    val duplicate = problem(id = "2", message = "duplicate")
    val viewModel = AIReviewViewModel(project, request)

    viewModel.setFullReviewState(AIReviewResult(request, listOf(problem, duplicate)))

    assertEquals(listOf(problem), viewModel.problems.value)
  }

  fun `test problems holder deduplicates collected problems`() {
    val problem = problem(id = "1", message = "duplicate")
    val duplicate = problem(id = "2", message = "duplicate")
    val holder = AIReviewProblemsHolder()

    holder.addProblems(listOf(problem, duplicate))

    assertEquals(mapOf("src/Main.java" to listOf(problem)), holder.getCollectedProblems())
  }

  private fun problem(id: String, message: String): AIReviewResult.Problem {
    return AIReviewResult.Problem(
      id = id,
      message = message,
      reasoning = "reasoning",
      path = "src/Main.java",
      lineStart = 1,
      lineEnd = 1,
      severity = AIReviewResult.Severity.Warning,
    )
  }
}
