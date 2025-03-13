// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.resultsProcessing

import com.intellij.ide.rpc.rpcId
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.frontend.SeItemDataFrontendProvider
import com.intellij.platform.searchEverywhere.frontend.SeItemDataLocalProvider
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.SeLog.ITEM_EMIT
import fleet.kernel.DurableRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.Internal

@OptIn(ExperimentalCoroutinesApi::class)
@Internal
class SeTabDelegate private constructor(val project: Project,
                                        private val logLabel: String,
                                        private val providers: Map<SeProviderId, SeItemDataProvider>) {
  private val providersAndLimits = providers.values.associate { it.id to Int.MAX_VALUE }

  fun getItems(params: SeParams): Flow<SeResultEvent> {
    val accumulator = SeResultsAccumulator(providersAndLimits)

    return providers.values.asFlow().flatMapMerge { provider ->
      provider.getItems(params).mapNotNull {
        SeLog.log(ITEM_EMIT) { "Tab delegate for ${logLabel} emits: ${it.presentation.text}" }
        accumulator.add(it)
      }
    }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)
  }

  suspend fun itemSelected(itemData: SeItemData, modifiers: Int, searchText: String): Boolean {
    val provider = providers[itemData.providerId] ?: return false
    return provider.itemSelected(itemData, modifiers, searchText)
  }

  companion object {
    private val LOG = Logger.getInstance(SeTabDelegate::class.java)

    suspend fun create(project: Project,
                       sessionRef: DurableRef<SeSessionEntity>,
                       logLabel: String,
                       providerIds: List<SeProviderId>,
                       dataContext: DataContext,
                       forceRemote: Boolean): SeTabDelegate {
      val dataContextId = readAction {
        dataContext.rpcId()
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
        SeItemDataFrontendProvider(project.projectId(), providerId, sessionRef, dataContextId)
      }

      val providers = frontendProviders + localProviders

      return SeTabDelegate(project, logLabel, providers)
    }
  }
}