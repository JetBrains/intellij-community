// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.ide.rpc.serializeToRpc
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.readAction
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
                                      private val providers: Map<SeProviderId, SeItemDataProvider>) {
  private val searchDispatcher: SeDispatcher

  init {
    searchDispatcher = SeDispatcher(providers.values, providers.values.associate { it.id to Int.MAX_VALUE })
  }

  fun getItems(params: SeParams): Flow<SeItemData> =
    searchDispatcher.getItems(sessionRef, params, emptyList())

  suspend fun itemSelected(itemData: SeItemData, modifiers: Int, searchText: String): Boolean {
    val provider = providers[itemData.providerId] ?: return false
    return provider.itemSelected(itemData, modifiers, searchText)
  }

  companion object {
    private val LOG = Logger.getInstance(SeTabHelper::class.java)

    suspend fun create(project: Project,
                       sessionRef: DurableRef<SeSessionEntity>,
                       providerIds: List<SeProviderId>,
                       dataContext: DataContext,
                       forceRemote: Boolean): SeTabHelper {
      val serializedDataContext = readAction {
        serializeToRpc(dataContext)
      }

      val allProviderIds = providerIds.toSet()

      val localProviders =
        if (forceRemote) emptyMap()
        else SeItemsProviderFactory.EP_NAME.extensionList.asFlow().filter {
          allProviderIds.contains(SeProviderId(it.id))
        }.mapNotNull {
          try {
            it.getItemsProvider(project, dataContext)
          }
          catch (e: Exception) {
            LOG.warn("SearchEverywhere item provider wasn't created. Exception: ${e.message}")
            null
          }
        }.toList().associate { provider ->
          SeProviderId(provider.id) to SeItemDataLocalProvider(provider, sessionRef)
        }

      val remoteProviderIds = allProviderIds - localProviders.keys.toSet()

      val frontendProviders = remoteProviderIds.associateWith { providerId ->
        SeItemDataFrontendProvider(project.projectId(), providerId, sessionRef, serializedDataContext)
      }

      val providers = frontendProviders + localProviders

      return SeTabHelper(project, sessionRef, providers)
    }
  }
}