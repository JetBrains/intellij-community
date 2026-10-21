// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private const val MAX_RESULTS: Int = 20
private const val MAX_LOGGED_RESULTS: Int = 5
private val SIMULATED_LATENCY: Duration = 500.milliseconds
private val WORD_SEPARATOR: Regex = Regex("[^\\p{L}\\p{N}]+")

private val LOG = logger<SeMockActionsRanker>()

/**
 * A local [SeActionsRanker] for the development of [SeRankerActionsProvider]. It sends no data to a remote model.
 *
 * It waits for [SIMULATED_LATENCY] to imitate a remote call.
 * Then it orders the candidates by the query words that their names and descriptions contain.
 * It logs each call, its result and its cancellation.
 *
 * To select this ranker, set the `search.everywhere.actions.ranker` registry key to `mock`.
 */
internal class SeMockActionsRanker : SeActionsRanker {
  override val id: String get() = "mock"

  override suspend fun rank(query: String, candidates: List<SeActionsRankerCandidate>): List<String> {
    LOG.info("Rank ${candidates.size} candidates for the query '$query'")
    if (candidates.size > maxCandidates) {
      LOG.warn("Got ${candidates.size} candidates, but the limit is $maxCandidates")
    }
    val start = TimeSource.Monotonic.markNow()
    try {
      delay(SIMULATED_LATENCY)

      val words = query.lowercase().split(WORD_SEPARATOR).filter { it.length > 1 }
      val rankedIds = candidates.asSequence()
        .map { it to score(it, words) }
        .filter { (_, score) -> score > 0 }
        .sortedByDescending { (_, score) -> score }
        .take(MAX_RESULTS)
        .map { (candidate, _) -> candidate.id }
        .toList()

      LOG.info("Ranked ${rankedIds.size} of ${candidates.size} candidates in ${start.elapsedNow()}. " +
               "The best matches: ${rankedIds.take(MAX_LOGGED_RESULTS)}")
      return rankedIds
    }
    finally {
      if (!currentCoroutineContext().isActive) {
        LOG.info("Ranking for the query '$query' is cancelled after ${start.elapsedNow()}")
      }
    }
  }

  /**
   * Gives 2 points for each query word in the name, and 1 point for each query word that only the description contains.
   */
  private fun score(candidate: SeActionsRankerCandidate, words: List<String>): Int {
    val name = candidate.name.lowercase()
    val description = candidate.description?.lowercase().orEmpty()
    return words.sumOf { word ->
      when (word) {
        in name -> 2
        in description -> 1
        else -> 0
      }
    }
  }
}
