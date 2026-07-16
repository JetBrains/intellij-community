// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.toolWindow.HighlightingProblem
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemLifetime
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
internal class ProblemLifetimeManager {

  private val problemIds = IdValueStore<Problem>()
  private val intentionIds = IdValueStore<IntentionAction>()
  private val problemToIntentions = ConcurrentHashMap<String, MutableSet<IntentionAction>>()

  fun getOrCreateHighlightingProblemId(problem: HighlightingProblem, lifetime: ProblemLifetime): String {
    val problemId = problemIds.getOrCreateId(problem, lifetime.coroutineScope)
    removeIntentionsOfProblem(problemId)
    return problemId
  }

  fun getOrCreateProblemId(problem: Problem, lifetime: ProblemLifetime): String {
    return problemIds.getOrCreateId(problem, lifetime.coroutineScope)
  }

  @OptIn(AwaitCancellationAndInvoke::class)
  fun createIntentionId(intention: IntentionAction, lifetime: ProblemLifetime, problemId: String): String {
    val intentionId = intentionIds.getOrCreateId(intention, lifetime.coroutineScope)

    problemToIntentions.computeIfAbsent(problemId) {
      lifetime.coroutineScope.awaitCancellationAndInvoke {
        problemToIntentions.remove(problemId)
      }
      ConcurrentHashMap.newKeySet()
    }.add(intention)

    return intentionId
  }

  fun removeProblemId(problem: Problem): String? {
    val problemId = problemIds.remove(problem) ?: return null
    removeIntentionsOfProblem(problemId)
    return problemId
  }

  fun findProblemById(id: String): Problem? {
    return problemIds.findValueById(id)
  }

  fun findIntentionById(id: String): IntentionAction? {
    return intentionIds.findValueById(id)
  }

  private fun removeIntentionsOfProblem(problemId: String) {
    problemToIntentions[problemId]?.let { intentions ->
      intentions.forEach { intentionIds.remove(it) }
      intentions.clear()
    }
  }

  /** Test-only: whether a problem id currently resolves in the store. */
  @TestOnly
  fun hasProblemId(id: String): Boolean = problemIds.findValueById(id) != null

  /** Test-only: total number of problem ids currently in the store. */
  @TestOnly
  fun getProblemIdsSize(): Int = problemIds.getSize()

  internal fun getDiagnosticSnapshot(): String = buildString {
    appendLine("Problem IDs Count: ${problemIds.getSize()}")
    appendLine("Intention IDs Count: ${intentionIds.getSize()}")
    appendLine("Problem-to-Intentions Mappings: ${problemToIntentions.size}")
    appendLine()

    appendLine("Problem IDs (first 20):")
    val problemSample = problemIds.getSample(20)
    if (problemSample.isEmpty()) {
      appendLine("  (empty)")
    } else {
      problemSample.forEach { (problem, id) ->
        appendLine("  $id -> ${problem.text}(hash=${problem.hashCode()})")
      }
    }
    appendLine()
  }

  companion object{
    fun getInstance(project: Project): ProblemLifetimeManager = project.service()
  }
}
