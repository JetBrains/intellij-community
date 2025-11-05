// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.ide.DataManager
import com.intellij.ide.actions.searcheverywhere.CheckBoxSearchEverywhereToggleAction
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereExtendedInfoProvider
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.AsyncProcessor
import com.intellij.platform.searchEverywhere.providers.SeAsyncContributorWrapper
import com.intellij.platform.searchEverywhere.providers.SeWrappedLegacyContributorItemsProvider
import com.intellij.platform.searchEverywhere.providers.getExtendedInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

@Internal
class SeActionItem(
  val matchedValue: MatchedValue,
  val weight: Int,
  override val contributor: SearchEverywhereContributor<*>,
  val extendedInfo: SeExtendedInfo?,
  val isMultiSelectionSupported: Boolean,
) : SeItem, SeLegacyItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation {
    return SeActionPresentationProvider.get(matchedValue, extendedInfo, isMultiSelectionSupported)
  }

  override val rawObject: Any get() = matchedValue
}

@Internal
class SeActionsAdaptedProvider(private val contributorWrapper: SeAsyncContributorWrapper<MatchedValue>) : SeWrappedLegacyContributorItemsProvider(), SeExtendedInfoProvider {
  override val id: String get() = SeProviderIdUtils.ACTIONS_ID
  override val displayName: @Nls String
    get() = contributor.fullGroupName
  override val contributor: SearchEverywhereContributor<*>
    get() = contributorWrapper.contributor

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val filter = SeActionsFilter.from(params.filter)
    contributor.getActions({}).filterIsInstance<CheckBoxSearchEverywhereToggleAction>().firstOrNull()?.let {
      it.setScopeIsDefaultAndAutoSet(filter.isAutoTogglePossible)
      it.isEverywhere = filter.includeDisabled
    }

    contributorWrapper.fetchElements(params.inputQuery, object : AsyncProcessor<MatchedValue> {
      override suspend fun process(item: MatchedValue, weight: Int): Boolean {
        return collector.put(SeActionItem(item, weight, contributor, contributor.getExtendedInfo(item), contributor.isMultiSelectionSupported))
      }
    })
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeActionItem)?.matchedValue ?: return false
    return withContext(Dispatchers.EDT) {
      contributorWrapper.contributor.processSelectedItem(legacyItem, modifiers, searchText)
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