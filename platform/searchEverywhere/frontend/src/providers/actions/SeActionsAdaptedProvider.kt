// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.CheckBoxSearchEverywhereToggleAction
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.providers.getExtendedDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls


@Internal
class SeActionsAdaptedProvider(private val legacyContributor: ActionSearchEverywhereContributor) : SeItemsProvider {
  override val id: String get() = ID
  override val displayName: @Nls String
    get() = legacyContributor.fullGroupName

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val inputQuery = params.inputQuery
    val filter = SeActionsFilter.from(params.filter)
    legacyContributor.getActions({}).filterIsInstance<CheckBoxSearchEverywhereToggleAction>().firstOrNull()?.let {
      it.isEverywhere = filter.includeDisabled
    }

    coroutineScope {
      legacyContributor.fetchWeightedElements(this, inputQuery) {
        collector.put(SeActionItem(it.item, getExtendedDescription(it.item)))
      }
    }
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeActionItem)?.matchedValue ?: return false
    return withContext(Dispatchers.EDT) {
      legacyContributor.processSelectedItem(legacyItem, modifiers, searchText)
    }
  }

  fun getExtendedDescription(item: MatchedValue): String? {
    return legacyContributor.getExtendedDescription(item)
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return legacyContributor.showInFindResults()
  }

  override fun dispose() {
    Disposer.dispose(legacyContributor)
  }

  companion object {
    const val ID: String = "com.intellij.ActionsItemsProvider"
  }
}