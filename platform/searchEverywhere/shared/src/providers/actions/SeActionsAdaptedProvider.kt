// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.actions

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeTextSearchParams
import com.intellij.platform.searchEverywhere.api.SeItem
import com.intellij.platform.searchEverywhere.api.SeItemsProvider
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.ApiStatus.Internal


@Internal
class SeActionsAdaptedProvider(val project: Project, private val legacyContributor: ActionSearchEverywhereContributor): SeItemsProvider {
  override val id: String get() = ID

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val textSearchParams = params as? SeTextSearchParams ?: return
    val text = textSearchParams.text
    val filter = SeActionsFilterData.fromTabData(textSearchParams.filterData)

    coroutineScope {
      legacyContributor.fetchWeightedElements(this, text) { t ->
        runBlockingCancellable {
          collector.put(SeActionItem(t.item))
        }
      }
    }
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val legacyItem = (item as? SeActionItem)?.matchedValue ?: return false
    return legacyContributor.synchronousContributor.processSelectedItem(legacyItem, modifiers, searchText)
  }

  companion object {
    const val ID: String = "com.intellij.ActionsItemsProvider"
  }
}