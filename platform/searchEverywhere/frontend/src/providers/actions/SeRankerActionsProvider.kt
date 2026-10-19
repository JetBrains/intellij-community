// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.platform.searchEverywhere.SeExtendedInfoProvider
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.providers.getExtendedInfo
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val MAX_QUERY_LENGTH: Int = 200
private val RANKER_TIMEOUT: Duration = 3.seconds

/**
 * Finds actions for a natural-language query with the selected [SeActionsRanker].
 *
 * This provider is not a part of the ML ranking of the Search Everywhere results.
 * The standard actions provider runs in parallel and does not depend on this provider.
 * When the ranker fails or times out, this provider returns no items.
 *
 * The provider uses [actionsProvider] for the item selection, the extended action and the item data.
 */
@Internal
class SeRankerActionsProvider(
  private val actionsProvider: SeActionsAdaptedProvider,
  private val ranker: SeActionsRanker,
) : SeItemsProvider by actionsProvider, SeExtendedInfoProvider {
  override val id: String get() = SeProviderIdUtils.RANKER_ACTIONS_ID
  override val displayName: @Nls String get() = actionsProvider.displayName

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val query = params.inputQuery.trim().take(MAX_QUERY_LENGTH)
    if (query.isEmpty()) return

    val candidates = selectCandidates()
    if (candidates.isEmpty()) return

    // Do not use withTimeout: SeLocalItemDataProvider retries a search that throws CancellationException.
    val rankedIds = withTimeoutOrNull(RANKER_TIMEOUT) {
      ranker.rank(query, candidates.map { it.rankerCandidate })
    } ?: return

    val candidatesById = candidates.associateBy { it.rankerCandidate.id }
    for ((index, rankedId) in rankedIds.withIndex()) {
      val candidate = candidatesById[rankedId] ?: continue
      if (!collector.put(createItem(candidate.matchedValue, rankedIds.size - index, params.inputQuery))) return
    }
  }

  /**
   * Selects a short list of actions to send to the ranker.
   */
  private fun selectCandidates(): List<Candidate> {
    // TODO IJPL-259050: select the candidates from the actions of the contributor model
    return emptyList()
  }

  private fun createItem(matchedValue: MatchedValue, weight: Int, searchText: String): SeActionItem {
    val contributor = actionsProvider.contributor
    return SeActionItem(matchedValue,
                        weight,
                        contributor,
                        contributor.getExtendedInfo(matchedValue),
                        contributor.isMultiSelectionSupported,
                        searchText,
                        SearchEverywhereSpellCheckResult.NoCorrection)
  }

  private class Candidate(val rankerCandidate: SeActionsRankerCandidate, val matchedValue: MatchedValue)
}
