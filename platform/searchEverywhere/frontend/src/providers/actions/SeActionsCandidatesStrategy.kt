// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.featureStatistics.ActionsLocalSummary
import com.intellij.internal.statistic.local.ActionsGlobalSummaryManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.RegistryManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private const val STRATEGY_REGISTRY_KEY: String = "search.everywhere.actions.ranker.candidates"

/**
 * The constant of the reciprocal rank fusion. A larger value gives less weight to the first places of each list.
 */
private const val RECIPROCAL_RANK_OFFSET: Int = 60

/**
 * The maximum part of the candidates of one [SeActionsCandidatesStrategy.POPULARITY] call that the settings options can take.
 */
private const val MAX_OPTIONS_SHARE: Double = 0.25

/**
 * Decides which candidates each [SeActionsRankerSession.rank] call gets
 * when there are more candidates than [SeActionsRankerSession.maxCandidates].
 *
 * The `search.everywhere.actions.ranker.candidates` registry key selects the strategy by its [registryOption].
 */
internal enum class SeActionsCandidatesStrategy(val registryOption: String) {
  /**
   * Sends the best settings options and the most popular actions to the ranker in one call.
   *
   * The options take up to [MAX_OPTIONS_SHARE] of the candidates.
   * They are the options that match the most query words, see [selectOptionsByQueryWords].
   * The actions take the other candidates.
   * The strategy merges two orders of the actions with the reciprocal rank fusion:
   * the share of all users who use the action, and the number of times the current user used it.
   */
  POPULARITY("popularity") {
    override suspend fun rankOverLimit(
      session: SeActionsRankerSession,
      query: String,
      candidates: List<SeActionsRankerCandidate>,
    ): List<SeActionsRankerMatch> {
      val limit = maxCandidates(session)
      val (options, actions) = candidates.partition { it.isOption }
      val selectedOptions = selectOptionsByQueryWords(query, options, (limit * MAX_OPTIONS_SHARE).toInt())
      val selectedActions = selectPopular(actions, limit - selectedOptions.size)
      SeRankerActionsProvider.LOG.debug {
        "$registryOption: 1 ranker call with ${selectedActions.size} of ${actions.size} actions " +
        "and ${selectedOptions.size} of ${options.size} options"
      }
      return session.rank(query, selectedActions + selectedOptions)
    }
  },

  /**
   * Sends all candidates to the ranker in parallel calls, one call for each part of the list.
   * Then it ranks the best candidates of all parts again in one more round.
   */
  SPLIT_AND_MERGE("splitAndMerge") {
    override suspend fun rankOverLimit(
      session: SeActionsRankerSession,
      query: String,
      candidates: List<SeActionsRankerCandidate>,
    ): List<SeActionsRankerMatch> {
      val limit = maxCandidates(session)
      val partCount = Math.ceilDiv(candidates.size, limit)
      val partSize = Math.ceilDiv(candidates.size, partCount)
      SeRankerActionsProvider.LOG.debug { "$registryOption: $partCount parallel ranker calls with up to $partSize of ${candidates.size} candidates each" }
      val partResults = coroutineScope {
        candidates.chunked(partSize).map { part -> async { session.rank(query, part) } }.awaitAll()
      }

      val finalistsPerPart = (limit / partCount).coerceAtLeast(1)
      val finalistIds = partResults.flatMapTo(HashSet()) { matches -> matches.take(finalistsPerPart).map { it.id } }
      SeRankerActionsProvider.LOG.debug { "$registryOption: ${finalistIds.size} finalists for the next round, up to $finalistsPerPart of each part" }
      if (finalistIds.isEmpty()) return emptyList()

      // The next round has fewer candidates. When they are still more than the limit, it splits them again.
      return rank(session, query, candidates.filter { it.id in finalistIds })
    }
  },

  /**
   * Sends all candidates to the ranker in one call and ignores [SeActionsRankerSession.maxCandidates].
   * Use it to find the real limit of a ranker.
   */
  UNLIMITED("unlimited") {
    override suspend fun rankOverLimit(
      session: SeActionsRankerSession,
      query: String,
      candidates: List<SeActionsRankerCandidate>,
    ): List<SeActionsRankerMatch> {
      SeRankerActionsProvider.LOG.debug { "$registryOption: 1 ranker call with ${candidates.size} candidates over the limit of ${maxCandidates(session)}" }
      return session.rank(query, candidates)
    }
  };

  /**
   * Orders [candidates] by how well they match [query] with [session].
   *
   * @return the matching candidates, the best match first.
   */
  suspend fun rank(session: SeActionsRankerSession, query: String, candidates: List<SeActionsRankerCandidate>): List<SeActionsRankerMatch> {
    if (candidates.size <= maxCandidates(session)) {
      SeRankerActionsProvider.LOG.debug { "$registryOption: 1 ranker call with ${candidates.size} candidates within the limit" }
      return session.rank(query, candidates)
    }

    return rankOverLimit(session, query, candidates)
  }

  /**
   * Orders [candidates] when there are more of them than [SeActionsRankerSession.maxCandidates]. Call [rank] instead.
   */
  abstract suspend fun rankOverLimit(
    session: SeActionsRankerSession,
    query: String,
    candidates: List<SeActionsRankerCandidate>,
  ): List<SeActionsRankerMatch>

  companion object {
    /**
     * Returns the strategy that the registry key selects, or [POPULARITY] when the key has an unknown value.
     */
    suspend fun findSelected(): SeActionsCandidatesStrategy {
      val option = RegistryManager.getInstanceAsync().get(STRATEGY_REGISTRY_KEY).selectedOption
      return entries.firstOrNull { it.registryOption == option } ?: POPULARITY
    }
  }
}

private fun maxCandidates(session: SeActionsRankerSession): Int = session.maxCandidates.coerceAtLeast(2)

private suspend fun selectPopular(candidates: List<SeActionsRankerCandidate>, limit: Int): List<SeActionsRankerCandidate> {
  val globalSummary = serviceAsync<ActionsGlobalSummaryManager>()
  val localStats = serviceAsync<ActionsLocalSummary>().getActionsStats()

  val globalRanks = rankBy(candidates) { candidate ->
    (globalSummary.getUpdatedStatistics(candidate.id) ?: globalSummary.getStatistics(candidate.id))?.usersRatio
  }
  val localRanks = rankBy(candidates) { candidate ->
    localStats[candidate.id]?.usageCount?.takeIf { it > 0 }?.toDouble()
  }

  return candidates
    .sortedByDescending { reciprocalRank(globalRanks[it.id]) + reciprocalRank(localRanks[it.id]) }
    .take(limit)
}

/**
 * Returns the place of each candidate in the order of [score], the highest score first. The first place is 1.
 * The result does not contain the candidates without a score.
 */
private fun rankBy(candidates: List<SeActionsRankerCandidate>, score: (SeActionsRankerCandidate) -> Double?): Map<String, Int> {
  return candidates
    .mapNotNull { candidate -> score(candidate)?.let { candidate.id to it } }
    .sortedByDescending { (_, value) -> value }
    .withIndex()
    .associate { (index, idToScore) -> idToScore.first to index + 1 }
}

private fun reciprocalRank(rank: Int?): Double = if (rank == null) 0.0 else 1.0 / (RECIPROCAL_RANK_OFFSET + rank)
