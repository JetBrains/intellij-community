// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFiltersAction
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.ide.ui.icons.rpcId
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SeTypeVisibilityStateProviderDelegate {
  fun <T> getStates(contributor: WeightedSearchEverywhereContributor<Any>, index: Int): List<SeTypeVisibilityStatePresentation> {
    val searchEverywhereFiltersAction: SearchEverywhereFiltersAction<T> = contributor.filterAction(index) ?: return emptyList()

    val filter = searchEverywhereFiltersAction.filter
    val elements = filter.allElements

    return elements.map { element ->
      SeTypeVisibilityStatePresentation(filter.getElementText(element),
                                        filter.getElementIcon(element)?.rpcId(),
                                        filter.isSelected(element))
    }
  }

  fun <T> applyTypeVisibilityStates(contributor: WeightedSearchEverywhereContributor<Any>, hiddenTypes: List<String>?) {
    if (hiddenTypes == null) return
    val searchEverywhereFiltersActions: List<SearchEverywhereFiltersAction<T>> = contributor.allFilterActions()

    searchEverywhereFiltersActions.forEach { action ->
      val hiddenTypesSet = hiddenTypes.toSet()
      val filter = action.filter
      val elements = filter.allElements

      elements.forEach {
        filter.setSelected(it, !hiddenTypesSet.contains(filter.getElementText(it)))
      }
    }
  }

  private fun <T> WeightedSearchEverywhereContributor<Any>.filterAction(index: Int): SearchEverywhereFiltersAction<T>? =
    allFilterActions<T>().getOrNull(index)

  private fun <T> WeightedSearchEverywhereContributor<Any>.allFilterActions(): List<SearchEverywhereFiltersAction<T>> =
    getActions { }.filterIsInstance<SearchEverywhereFiltersAction<T>>()
}