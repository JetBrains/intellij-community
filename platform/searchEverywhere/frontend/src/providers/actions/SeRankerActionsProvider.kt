// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.GotoActionModel.ActionWrapper
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValueType
import com.intellij.ide.util.gotoByName.MatchMode
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.Utils.runUpdateSessionForActionSearch
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeExtendedInfoProvider
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.providers.getExtendedInfo
import com.intellij.platform.util.coroutines.mapNotNullConcurrent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val MAX_QUERY_LENGTH: Int = 200
private val RANKER_TIMEOUT: Duration = 3.seconds

/**
 * Finds actions for a natural-language query with the selected [SeActionsRanker].
 * It opens one [SeActionsRankerSession] of the ranker for the popup.
 *
 * This provider is not a part of the ML ranking of the Search Everywhere results.
 * The standard actions provider runs in parallel and does not depend on this provider.
 * When the ranker fails or times out, this provider returns no items.
 *
 * The provider uses [actionsProvider] for the item selection, the extended action and the item data.
 * It takes the actions and their presentations from [actionModel], the model of the [actionsProvider] contributor.
 * The [candidatesStrategy] decides which candidates each ranker call gets.
 */
@Internal
class SeRankerActionsProvider internal constructor(
  private val actionsProvider: SeActionsAdaptedProvider,
  private val actionModel: GotoActionModel,
  private val ranker: SeActionsRanker,
  private val candidatesStrategy: SeActionsCandidatesStrategy,
) : SeItemsProvider by actionsProvider, SeExtendedInfoProvider {
  private val allCandidatesMutex = Mutex()
  private var allCandidates: List<Candidate>? = null

  private val sessionMutex = Mutex()
  private var session: SeActionsRankerSession? = null

  override val id: String get() = SeProviderIdUtils.RANKER_ACTIONS_ID
  override val displayName: @Nls String get() = actionsProvider.displayName

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val query = params.inputQuery.trim().take(MAX_QUERY_LENGTH)
    if (query.isEmpty()) return

    // Do not use withTimeout: SeLocalItemDataProvider retries a search that throws CancellationException.
    val session = withTimeoutOrNull(RANKER_TIMEOUT) { getSession() } ?: return

    val candidates = selectCandidates(SeActionsFilter.from(params.filter).includeDisabled)
    if (candidates.isEmpty()) return

    val rankedIds = withTimeoutOrNull(RANKER_TIMEOUT) {
      candidatesStrategy.rank(session, query, candidates.map { it.rankerCandidate })
    } ?: return

    val candidatesById = candidates.associateBy { it.rankerCandidate.id }
    for ((index, rankedId) in rankedIds.withIndex()) {
      val candidate = candidatesById[rankedId] ?: continue
      if (!collector.put(createItem(candidate.actionWrapper, rankedIds.size - index, params.inputQuery))) return
    }
  }

  /**
   * Returns the session of [ranker] for this popup, or `null` when the ranker cannot rank now.
   *
   * The provider opens the session on the first query and keeps it until the popup closes.
   * The session is a child of this provider, thus Search Everywhere disposes it together with the provider.
   */
  private suspend fun getSession(): SeActionsRankerSession? = sessionMutex.withLock {
    session?.let { return@withLock it }

    val opened = ranker.openSession() ?: return@withLock null
    if (!Disposer.tryRegister(this, opened)) {
      // The popup closed while the session opened.
      Disposer.dispose(opened)
      return@withLock null
    }
    session = opened
    opened
  }

  /**
   * Selects the actions to send to the ranker: the actions that are enabled and visible in the current context.
   * When [includeDisabled] is `true`, it also selects the other actions, as the standard actions search does.
   */
  private suspend fun selectCandidates(includeDisabled: Boolean): List<Candidate> {
    return getAllCandidates().filter { includeDisabled || it.actionWrapper.isAvailable }
  }

  /**
   * Returns all searchable actions that have a name, with their presentations in the current context.
   *
   * The provider collects the list on the first query and reuses it, because the context does not change while the popup is open.
   * When the first query is cancelled, the next query collects the list again.
   */
  private suspend fun getAllCandidates(): List<Candidate> = allCandidatesMutex.withLock {
    allCandidates ?: collectAllCandidates().also { allCandidates = it }
  }

  private suspend fun collectAllCandidates(): List<Candidate> = coroutineScope {
    actionModel.buildGroupMappings()
    val actionManager = serviceAsync<ActionManager>()

    runUpdateSessionForActionSearch(actionModel.updateSession) { presentationProvider ->
      actionManager.getActionIdList("")
        .mapNotNullConcurrent { actionId -> createCandidate(actionManager, actionId, presentationProvider) }
        .sortedBy { it.rankerCandidate.id }
    }.await()
  }

  private suspend fun createCandidate(
    actionManager: ActionManager,
    actionId: String,
    presentationProvider: suspend (AnAction) -> Presentation,
  ): Candidate? {
    val action = actionManager.getAction(actionId) ?: return null
    if (action is ActionGroup && !action.isSearchable) return null

    val groupMapping = actionModel.getGroupMapping(action)
    groupMapping?.updateBeforeShowSuspend(presentationProvider)
    val presentation = presentationProvider(action)
    val name = presentation.text?.takeIf { it.isNotBlank() } ?: return null

    return Candidate(SeActionsRankerCandidate(actionId, name, presentation.description),
                     ActionWrapper(action, groupMapping, MatchMode.NAME, presentation))
  }

  private fun createItem(actionWrapper: ActionWrapper, weight: Int, searchText: String): SeActionItem {
    val matchedValue = MatchedValue(actionWrapper, searchText, MatchedValueType.ACTION)
    val contributor = actionsProvider.contributor
    return SeActionItem(matchedValue,
                        weight,
                        contributor,
                        contributor.getExtendedInfo(matchedValue),
                        contributor.isMultiSelectionSupported,
                        searchText,
                        SearchEverywhereSpellCheckResult.NoCorrection)
  }

  private class Candidate(val rankerCandidate: SeActionsRankerCandidate, val actionWrapper: ActionWrapper)
}
