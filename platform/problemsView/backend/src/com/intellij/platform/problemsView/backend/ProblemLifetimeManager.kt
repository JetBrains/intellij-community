// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.toolWindow.HighlightingProblem
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemLifetime
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
internal class ProblemLifetimeManager {

  private val problemIds = IdValueStore<Problem>()
  private val intentionIds = IdValueStore<IntentionAction>()
  private val problemToIntentions = ConcurrentHashMap<String, MutableSet<IntentionAction>>()

  fun getOrCreateHighlightingProblemId(problem: HighlightingProblem, lifetime: ProblemLifetime): String {
    val problemId = getOrCreateProblemId(problem, lifetime)
    removeAllIntentionsOfProblem(problemId).forEach(lifetime::unbindId)
    return problemId
  }

  fun getOrCreateProblemId(problem: Problem, lifetime: ProblemLifetime): String {
    val id = problemIds.getOrCreateId(problem)
    lifetime.bindIdToLifetime(id)

    return id
  }

  fun createIntentionId(intention: IntentionAction, lifetime: ProblemLifetime, problemId: String): String {
    val id = intentionIds.getOrCreateId(intention)
    problemToIntentions.computeIfAbsent(problemId) { ConcurrentHashMap.newKeySet() }.add(intention)

    if (!lifetime.bindIdToLifetime(id)) {
      removeIntentionFromProblem(problemId, intention)
    }

    return id
  }

  fun removeProblemId(problem: Problem, lifetime: ProblemLifetime): String? {
    val problemId = problemIds.remove(problem) ?: return null

    lifetime.unbindId(problemId)
    removeAllIntentionsOfProblem(problemId).forEach(lifetime::unbindId)

    return problemId
  }

  fun findProblemById(id: String): Problem? {
    return problemIds.findValueById(id)
  }

  fun findIntentionById(id: String): IntentionAction? {
    return intentionIds.findValueById(id)
  }

  private fun ProblemLifetime.bindIdToLifetime(id: String): Boolean {
    ensureCompletionHandlerRegistered {
      unbindAllIds().forEach(::removeStoredId)
    }

    if (bindId(id)) return true

    // the lifetime completed before id could be bound, so we remove it explicitly from the IdValueStore
    removeStoredId(id)
    return false
  }

  private fun removeStoredId(id: String) {
    if (problemIds.removeById(id)) {
      removeAllIntentionsOfProblem(id)
    }
    else {
      intentionIds.removeById(id)
    }
  }

  private fun removeAllIntentionsOfProblem(problemId: String): List<String> =
    problemToIntentions.remove(problemId)?.mapNotNull(intentionIds::remove).orEmpty()

  private fun removeIntentionFromProblem(problemId: String, intention: IntentionAction) {
    val intentions = problemToIntentions[problemId] ?: return
    intentions.remove(intention)

    if (intentions.isEmpty()) {
      problemToIntentions.remove(problemId, intentions)
    }
  }

  /** Test-only: whether a problem id currently resolves in the store. */
  @TestOnly
  fun hasProblemId(id: String): Boolean = problemIds.findValueById(id) != null

  /** Test-only: total number of problem ids currently in the store. */
  @TestOnly
  fun getProblemIdsSize(): Int = problemIds.getSize()

  companion object {
    fun getInstance(project: Project): ProblemLifetimeManager = project.service()
  }
}
