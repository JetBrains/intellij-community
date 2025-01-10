// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.api.SeItemDataProvider
import com.intellij.platform.searchEverywhere.api.SeItemsProviderFactory
import com.intellij.platform.searchEverywhere.frontend.dispatcher.SeDispatcher
import fleet.kernel.DurableRef
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeTabHelper private constructor(val project: Project,
                                      private val sessionRef: DurableRef<SeSessionEntity>,
                                      providers: Map<SeProviderId, SeItemDataProvider>) {
  private val searchDispatcher: SeDispatcher

  init {
    val providerLimit = if (providers.size > 1) MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT else SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT
    searchDispatcher = SeDispatcher(providers.values, providers.values.associate { it.id to providerLimit })
  }

  fun getItems(params: SeParams): Flow<SeItemData> =
    searchDispatcher.getItems(sessionRef, params, emptyList())

  companion object {
    private const val SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT: Int = 30
    private const val MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT: Int = 15
    private val LOG = Logger.getInstance(SeTabHelper::class.java)

    suspend fun create(project: Project,
                       sessionRef: DurableRef<SeSessionEntity>,
                       providerIds: List<SeProviderId>,
                       forceRemote: Boolean): SeTabHelper {

      val allProviderIds = providerIds.toSet()

      val localProviders =
        if (forceRemote) emptyMap()
        else SeItemsProviderFactory.EP_NAME.extensionList.asFlow().mapNotNull {
          try {
            it.getItemsProvider(project)
          }
          catch (e: Exception) {
            LOG.warn("SearchEverywhere item provider wasn't created. Exception: ${e.message}")
            null
          }
        }.filter {
          allProviderIds.contains(SeProviderId(it.id))
        }.toList().associate { provider ->
          SeProviderId(provider.id) to SeItemDataLocalProvider(provider)
        }

      val remoteProviderIds = allProviderIds - localProviders.keys.toSet()

      val frontendProviders = remoteProviderIds.associateWith { providerId ->
        SeItemDataFrontendProvider(project.projectId(), providerId)
      }

      val providers = frontendProviders + localProviders

      return SeTabHelper(project, sessionRef, providers)
    }
  }
}