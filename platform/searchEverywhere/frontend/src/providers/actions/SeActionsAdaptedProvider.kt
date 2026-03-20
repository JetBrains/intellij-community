// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.ide.DataManager
import com.intellij.ide.actions.searcheverywhere.CheckBoxSearchEverywhereToggleAction
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrector
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereExtendedInfoProvider
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.platform.searchEverywhere.SeExtendedInfoProvider
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeLegacyItem
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.providers.AsyncProcessor
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.SeWrappedLegacyContributorItemsProvider
import com.intellij.platform.searchEverywhere.providers.getExtendedInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls


private const val MAX_TYPO_TOLERANT_CANDIDATES: Int = 2


@Internal
interface SeSpellCorrectionAwareItem {
  val correction: SearchEverywhereSpellCheckResult
  val effectiveSearchText: String
}

@Internal
class SeActionItem(
  val matchedValue: MatchedValue,
  val weight: Int,
  override val contributor: SearchEverywhereContributor<*>,
  val extendedInfo: SeExtendedInfo?,
  val isMultiSelectionSupported: Boolean,
  override val effectiveSearchText: String,
  override val correction: SearchEverywhereSpellCheckResult,
) : SeItem, SeLegacyItem, SeSpellCorrectionAwareItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation {
    return SeActionPresentationProvider.get(matchedValue, extendedInfo, isMultiSelectionSupported)
  }

  override val rawObject: Any get() = matchedValue
}

@Internal
data class SeActionQueryVariant(
  val searchText: String,
  val correction: SearchEverywhereSpellCheckResult,
)

@Internal
class SeActionsAdaptedProvider(
  private val contributorWrapper: SeAsyncContributorWrapper<MatchedValue>,
  private val isTypoTolerantSearchEnabled: Boolean = false,
) : SeWrappedLegacyContributorItemsProvider(), SeExtendedInfoProvider {
  override val id: String get() = SeProviderIdUtils.ACTIONS_ID
  override val displayName: @Nls String
    get() = contributor.fullGroupName
  override val contributor: SearchEverywhereContributor<*>
    get() = contributorWrapper.contributor

  private val spellingCorrector: SearchEverywhereSpellingCorrector? = SearchEverywhereSpellingCorrector.getInstance()
    ?.takeIf { isTypoTolerantSearchEnabled && it.isAvailableInTab(this.contributor.searchProviderId) }

  private fun getQueryVariants(inputQuery: String): List<SeActionQueryVariant> {
    if (spellingCorrector == null) {
      return listOf(SeActionQueryVariant(inputQuery, SearchEverywhereSpellCheckResult.NoCorrection))
    }

    return buildList {
      add(SeActionQueryVariant(inputQuery, SearchEverywhereSpellCheckResult.NoCorrection))

      addAll(
        spellingCorrector.getAllCorrections(inputQuery, MAX_TYPO_TOLERANT_CANDIDATES)
          .map { correction -> SeActionQueryVariant(correction.correction, correction) }
      )
    }.distinctBy { it.searchText }
  }

  private fun createItem(item: MatchedValue, weight: Int, queryVariant: SeActionQueryVariant): SeActionItem {
    return SeActionItem(item,
                        weight,
                        contributor,
                        contributor.getExtendedInfo(item),
                        contributor.isMultiSelectionSupported,
                        queryVariant.searchText,
                        queryVariant.correction)
  }

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val filter = SeActionsFilter.from(params.filter)
    contributor.getActions({}).filterIsInstance<CheckBoxSearchEverywhereToggleAction>().firstOrNull()?.let {
      it.setScopeIsDefaultAndAutoSet(filter.isAutoTogglePossible)
      it.isEverywhere = filter.includeDisabled
    }

    coroutineScope {
      getQueryVariants(params.inputQuery).forEach { queryVariant ->
        launch {
          contributorWrapper.fetchElements(queryVariant.searchText, object : AsyncProcessor<MatchedValue> {
            override suspend fun process(item: MatchedValue, weight: Int): Boolean {
              val keepGoing = collector.put(createItem(item, weight, queryVariant))
              if (!keepGoing) {
                this@coroutineScope.cancel()
              }
              return keepGoing
            }
          })
        }
      }
    }
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val actionItem = item as? SeActionItem ?: return false
    val selectionText = when (actionItem.correction) {
      is SearchEverywhereSpellCheckResult.Correction -> actionItem.effectiveSearchText
      SearchEverywhereSpellCheckResult.NoCorrection -> searchText
    }
    return withContext(Dispatchers.EDT) {
      contributorWrapper.contributor.processSelectedItem(actionItem.matchedValue, modifiers, selectionText)
    }
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return contributor.showInFindResults()
  }

  override suspend fun performExtendedAction(item: SeItem): Boolean {
    val legacyItem = (item as? SeActionItem)?.matchedValue ?: return false
    val rightAction = (contributor as? SearchEverywhereExtendedInfoProvider)
                        ?.createExtendedInfo()?.rightAction?.invoke(legacyItem) ?: return false

    return withContext(Dispatchers.EDT) {
      val result = CompletableDeferred<Boolean>()
      DataManager.getInstance().getDataContextFromFocusAsync().onSuccess { context ->
        rightAction.actionPerformed(AnActionEvent.createEvent(
          context,
          Presentation(),
          ActionPlaces.ACTION_SEARCH,
          ActionUiKind.SEARCH_POPUP,
          null
        ))
        result.complete(true)
      }.onError {
        result.complete(false)
      }
      return@withContext result.await()
    }
  }

  override fun dispose() {
    Disposer.dispose(contributor)
  }
}