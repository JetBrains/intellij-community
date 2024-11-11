// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere.core

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.searchEverywhere.ActionItemPresentation
import com.intellij.searchEverywhere.SearchEverywhereListItem
import com.intellij.searchEverywhere.SearchEverywhereViewItemsProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SearchEverywhereRequestHandler {
  suspend fun search(providers: Collection<SearchEverywhereViewItemsProvider<*, *, *>>,
                     pattern: String,
                     providerLimit: Int? = null,
                     alreadyFoundResults: List<SearchEverywhereListItem<*, *>>): SharedFlow<List<SearchEverywhereListItem<*, *>>>
}

@ApiStatus.Internal
class DefaultSearchEverywhereRequestHandler: SearchEverywhereRequestHandler {

  override suspend fun search(providers: Collection<SearchEverywhereViewItemsProvider<*, *, *>>,
                              pattern: String,
                              providerLimit: Int?,
                              alreadyFoundResults: List<SearchEverywhereListItem<*, *>>): SharedFlow<List<SearchEverywhereListItem<*, *>>> {
    val item = "Hello world"

    return MutableStateFlow(listOf(SearchEverywhereListItem(item = item,
                                                            ActionItemPresentation(name = item),
                                                            weight = 0,
                                                            dataContext = DataContext.EMPTY_CONTEXT,
                                                            textDescription = null)))
  }
}