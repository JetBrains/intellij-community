// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.actions

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.ApiStatus.Internal


@Internal
class SeActionsAdaptedProvider(val project: Project, private val legacyContributor: ActionSearchEverywhereContributor): SeItemsProvider {
  override val id: String get() = ID

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val inputQuery = params.inputQuery
    val filter = SeActionsFilterData.from(params.filter)

    coroutineScope {
      legacyContributor.fetchWeightedElements(this, inputQuery) {
        collector.put(SeActionItem(it.item))
      }
    }
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeActionItem)?.matchedValue ?: return false
    return legacyContributor.processSelectedItem(legacyItem, modifiers, searchText)
  }

  companion object {
    const val ID: String = "com.intellij.ActionsItemsProvider"
  }
}