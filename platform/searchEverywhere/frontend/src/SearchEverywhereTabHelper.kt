// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.frontend.dispatcher.SearchEverywhereDispatcher
import fleet.kernel.DurableRef
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SearchEverywhereTabHelper private constructor(val project: Project,
                                                    private val sessionRef: DurableRef<SearchEverywhereSessionEntity>,
                                                    providers: Map<SearchEverywhereProviderId, SearchEverywhereItemDataProvider>) {
  private val searchDispatcher: SearchEverywhereDispatcher

  init {
    val providerLimit = if (providers.size > 1) MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT else SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT
    searchDispatcher = SearchEverywhereDispatcher(providers.values, providers.values.associate { it.id to providerLimit })
  }

  fun getItems(params: SearchEverywhereParams): Flow<SearchEverywhereItemData> =
    searchDispatcher.getItems(sessionRef, params, emptyList())

  companion object {
    private const val SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT: Int = 30
    private const val MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT: Int = 15

    suspend fun create(project: Project,
                       sessionRef: DurableRef<SearchEverywhereSessionEntity>,
                       providerIds: List<SearchEverywhereProviderId>,
                       forceRemote: Boolean): SearchEverywhereTabHelper {

      val allProviderIds = providerIds.toSet()

      val localProviders =
        if (forceRemote) emptyMap()
        else SearchEverywhereItemsProviderFactory.EP_NAME.extensionList.asFlow().map {
          it.getItemsProvider(project)
        }.filter {
          allProviderIds.contains(SearchEverywhereProviderId(it.id))
        }.toList().associate { provider ->
          SearchEverywhereProviderId(provider.id) to SearchEverywhereItemDataLocalProvider(provider)
        }

      val remoteProviderIds = allProviderIds - localProviders.keys.toSet()

      val frontendProviders = remoteProviderIds.associateWith { providerId ->
        SearchEverywhereItemDataFrontendProvider(project.projectId(), providerId)
      }

      val providers = frontendProviders + localProviders

      return SearchEverywhereTabHelper(project, sessionRef, providers)
    }
  }
}