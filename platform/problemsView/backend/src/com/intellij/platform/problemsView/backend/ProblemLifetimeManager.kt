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
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

// history of problem events for debugging purposes (IJPL-248970)
internal data class ProblemHistory(
  val problemType: String,
  val text: String,
  val timesIdCreated: Int,
  val timesIdRemoved: Int,
  val lastCreatedTsMs: Long,
  val lastRemovedTsMs: Long,
)

private const val MAX_FILE_HISTORY = 4000
private const val MAX_PROJECT_ERRORS_HISTORY = 500

private const val initialHashMapCapacity = 256
private const val loadFactor = 0.75f


private class ProblemEventHistory(private val maxSize: Int) {
  private val histories: MutableMap<Int, ProblemHistory> =
    Collections.synchronizedMap(object : LinkedHashMap<Int, ProblemHistory>(
      initialHashMapCapacity,
      loadFactor,
      true) {
          override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ProblemHistory>): Boolean = size > maxSize
    })

  operator fun get(problem: Problem): ProblemHistory? = histories[problem.hashCode()]

  fun recordIdCreated(problem: Problem) {
    val problemHashCode = problem.hashCode()
    val now = System.currentTimeMillis()
    val problemHistory = histories[problemHashCode]

    histories[problemHashCode] = problemHistory?.copy(
      timesIdCreated = problemHistory.timesIdCreated + 1,
      lastCreatedTsMs = now
    ) ?: ProblemHistory(
      problem.javaClass.name,
      problem.text.take(200),
      timesIdCreated = 1,
      timesIdRemoved = 0,
      lastCreatedTsMs = now,
      lastRemovedTsMs = -1
    )
  }

  fun recordIdRemoved(problem: Problem) {
    val problemHashCode = problem.hashCode()
    val problemHistory = histories[problemHashCode] ?: return
    histories[problemHashCode] = problemHistory.copy(
      timesIdRemoved = problemHistory.timesIdRemoved + 1,
      lastRemovedTsMs = System.currentTimeMillis()
    )
  }
}

@Service(Service.Level.PROJECT)
internal class ProblemLifetimeManager {

  private val problemIds = IdValueStore<Problem>()
  private val intentionIds = IdValueStore<IntentionAction>()
  private val problemToIntentions = ConcurrentHashMap<String, MutableSet<IntentionAction>>()

  fun getOrCreateHighlightingProblemId(problem: HighlightingProblem, lifetime: ProblemLifetime): String {
    val existed = problemIds.hasKey(problem)
    val problemId = problemIds.getOrCreateId(problem, lifetime.coroutineScope)
    if (!existed) recordIdCreatedForLogs(problem)
    removeIntentionsOfProblem(problemId)
    return problemId
  }

  fun getOrCreateProblemId(problem: Problem, lifetime: ProblemLifetime): String {
    val existed = problemIds.hasKey(problem)
    val id = problemIds.getOrCreateId(problem, lifetime.coroutineScope)
    if (!existed) recordIdCreatedForLogs(problem)
    return id
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
    recordIdRemovedForLogs(problem)
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

  // history for logs helpers
  private val fileHistory = ProblemEventHistory(MAX_FILE_HISTORY)
  private val projectErrorsHistory = ProblemEventHistory(MAX_PROJECT_ERRORS_HISTORY)

  private fun historyFor(problem: Problem): ProblemEventHistory =
    if (problem is HighlightingProblem) fileHistory else projectErrorsHistory

  fun hasIdFor(problem: Problem): Boolean = problemIds.hasKey(problem)

  fun problemIdStoreSize(): Int = problemIds.getSize()

  fun getProblemHistory(problem: Problem): ProblemHistory? = historyFor(problem)[problem]

  private fun recordIdCreatedForLogs(problem: Problem) = historyFor(problem).recordIdCreated(problem)

  private fun recordIdRemovedForLogs(problem: Problem) = historyFor(problem).recordIdRemoved(problem)

  companion object{
    fun getInstance(project: Project): ProblemLifetimeManager = project.service()
  }
}
