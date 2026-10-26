// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.GotoActionModel.ActionWrapper
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValueType
import com.intellij.ide.util.gotoByName.MatchMode
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.Locale
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
 *
 * Search Everywhere starts a search on each keystroke and cancels the previous one.
 * The provider waits [debounce] before it asks the ranker, thus the ranker gets a query only after the user stops typing.
 */
@Internal
class SeRankerActionsProvider internal constructor(
  private val actionsProvider: SeActionsAdaptedProvider,
  private val actionModel: GotoActionModel,
  private val ranker: SeActionsRanker,
  private val candidatesStrategy: SeActionsCandidatesStrategy,
  private val debounce: Duration,
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

    delay(debounce)

    // Do not use withTimeout: SeLocalItemDataProvider retries a search that throws CancellationException.
    val session = withTimeoutOrNull(RANKER_TIMEOUT) { getSession() } ?: return

    val candidates = selectCandidates(SeActionsFilter.from(params.filter).includeDisabled)
    if (candidates.isEmpty()) return

    val matches = withTimeoutOrNull(RANKER_TIMEOUT) {
      candidatesStrategy.rank(session, query, candidates.map { it.rankerCandidate })
    } ?: return

    val candidatesById = candidates.associateBy { it.rankerCandidate.id }
    for ((index, match) in matches.withIndex()) {
      val candidate = candidatesById[match.id] ?: continue
      val diagnostics = rankerDiagnostics(ranker.diagnosticName, match)
      val item = createItem(candidate.actionWrapper, matches.size - index, params.inputQuery, diagnostics)
      if (!collector.put(item)) return
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
   * Returns all actions that can be candidates, with their presentations in the current context. See [createCandidate].
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

  /**
   * Creates the candidate of [actionId], or returns `null` when the action has no name or can hold user data.
   *
   * The ranker gets the template texts of the action. The presentation in the context can name a project, a file or a run configuration.
   * Only an action that the usage statistics can report is a candidate, see [ActionsCollectorImpl.canReportActionId].
   * Such an action comes from the descriptor of the platform, a JetBrains plugin or a Marketplace plugin, thus its template has no user data.
   * An action that a plugin registers at runtime can hold user data, such as the name of an external tool.
   */
  private suspend fun createCandidate(
    actionManager: ActionManager,
    actionId: String,
    presentationProvider: suspend (AnAction) -> Presentation,
  ): Candidate? {
    if (!ActionsCollectorImpl.canReportActionId(actionId)) return null
    val action = actionManager.getAction(actionId) ?: return null
    if (action is ActionGroup && !action.isSearchable) return null

    val template = action.templatePresentation
    val name = template.text?.takeIf { it.isNotBlank() } ?: return null

    val groupMapping = actionModel.getGroupMapping(action)
    groupMapping?.updateBeforeShowSuspend(presentationProvider)
    val presentation = presentationProvider(action)
    if (presentation.text.isNullOrBlank()) return null

    return Candidate(SeActionsRankerCandidate(actionId, name, template.description),
                     ActionWrapper(action, groupMapping, MatchMode.NAME, presentation))
  }

  private fun createItem(actionWrapper: ActionWrapper, weight: Int, searchText: String, diagnostics: @NonNls String?): SeActionItem {
    val matchedValue = MatchedValue(actionWrapper, searchText, MatchedValueType.ACTION)
    val contributor = actionsProvider.contributor
    return SeActionItem(matchedValue,
                        weight,
                        contributor,
                        contributor.getExtendedInfo(matchedValue),
                        contributor.isMultiSelectionSupported,
                        searchText,
                        SearchEverywhereSpellCheckResult.NoCorrection,
                        diagnostics)
  }

  private class Candidate(val rankerCandidate: SeActionsRankerCandidate, val actionWrapper: ActionWrapper)
}

/**
 * Returns the diagnostic text of [match] for the result list: the [rankerName], and the probability when the ranker gave one.
 * See [com.intellij.platform.searchEverywhere.SeItemWithDiagnostics].
 */
internal fun rankerDiagnostics(rankerName: @NonNls String, match: SeActionsRankerMatch): @NonNls String {
  val probability = match.probability ?: return rankerName
  return String.format(Locale.ROOT, "%s p=%.3f", rankerName, probability)
}
