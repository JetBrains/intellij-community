// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.frontend.dispatcher.SearchEverywhereDispatcher
import com.jetbrains.rhizomedb.EID
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SearchEverywhereTabHelper(val project: Project, private val sessionId: EID, providerIds: List<SearchEverywhereProviderId>) {
  private val providers: Map<SearchEverywhereProviderId, SearchEverywhereItemDataProvider>
  private val searchDispatcher: SearchEverywhereDispatcher

  init {
    val allProviderIds = providerIds.toSet()

    val localProviders = SearchEverywhereItemsProviderFactory.EP_NAME.extensionList.map {
      it.getItemsProvider()
    }.filter {
      allProviderIds.contains(SearchEverywhereProviderId(it.id))
    }.associate { provider ->
      SearchEverywhereProviderId(provider.id) to SearchEverywhereItemDataLocalProvider(provider)
    }

    val remoteProviderIds = allProviderIds - localProviders.keys.toSet()

    val frontendProviders = remoteProviderIds.associateWith {
      providerId -> SearchEverywhereItemDataFrontendProvider(project.projectId(), providerId)
    }

    providers = frontendProviders + localProviders

    val providerLimit = if (providers.size > 1) MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT else SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT

    searchDispatcher = SearchEverywhereDispatcher(providers.values, providers.values.associate { it.id to providerLimit })
  }

  fun getItems(params: SearchEverywhereParams): Flow<SearchEverywhereItemData> =
    searchDispatcher.getItems(sessionId, params, emptyList())

  companion object {
    private const val SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT: Int = 30
    private const val MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT: Int = 15
  }
}