// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere.core

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.searchEverywhere.SearchEverywhereItemPresentation
import com.intellij.searchEverywhere.SearchEverywhereItemsProvider
import com.intellij.searchEverywhere.SearchEverywhereViewItem
import com.intellij.searchEverywhere.SearchEverywhereViewItemsProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DefaultViewItemsProvider<Item, Presentation : SearchEverywhereItemPresentation, Params>(
  private val searchProvider: SearchEverywhereItemsProvider<Item, Params>,
  private val presentationRenderer: (Item) -> Presentation,
  private val dataContextRenderer: (Item) -> DataContext,
  private val descriptionRenderer: (Item) -> String? = { null },
) : SearchEverywhereViewItemsProvider<Item, Presentation, Params> {

  override fun processViewItems(searchParams: Params): Flow<SearchEverywhereViewItem<Item, Presentation>> =
    searchProvider.processItems(searchParams).map { weightedItem ->
      val item = weightedItem.item
      val weight = weightedItem.weight
      SearchEverywhereViewItem(item, presentationRenderer(item), weight, dataContextRenderer(item), descriptionRenderer(item))
    }
}