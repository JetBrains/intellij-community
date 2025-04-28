// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.resultsProcessing

import com.intellij.ide.rpc.rpcId
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.project.projectId
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.frontend.SeFrontendItemDataProvider
import com.intellij.platform.searchEverywhere.frontend.SeLocalItemDataProvider
import com.intellij.platform.searchEverywhere.impl.SeRemoteApi
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.SeLog.ITEM_EMIT
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import fleet.kernel.DurableRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

@OptIn(ExperimentalCoroutinesApi::class)
@Internal
class SeTabDelegate private constructor(val project: Project,
                                        private val logLabel: String,
                                        private val providers: Map<SeProviderId, SeItemDataProvider>): Disposable {
  private val providersAndLimits = providers.values.associate { it.id to Int.MAX_VALUE }
  val providersIdToName: Map<SeProviderId, @Nls String> = providers.mapValues { it.value.displayName }

  fun getItems(params: SeParams, disabledProviders: List<SeProviderId>? = null): Flow<SeResultEvent> {
    val accumulator = SeResultsAccumulator(providersAndLimits)
    val filteredProviders = disabledProviders?.let { providers.filterKeys { it !in disabledProviders } } ?: providers

    return filteredProviders.values.asFlow().flatMapMerge { provider ->
      provider.getItems(params).mapNotNull {
        SeLog.log(ITEM_EMIT) { "Tab delegate for ${logLabel} emits: ${it.presentation.text}" }
        accumulator.add(it)
      }
    }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)
  }

  suspend fun getSearchScopesInfos(): List<SeSearchScopesInfo> {
    return providers.values.mapNotNull { it.getSearchScopesInfo() }
  }

  suspend fun getTypeVisibilityStates(): List<SeTypeVisibilityStatePresentation> {
    return providers.values.flatMap { it.getTypeVisibilityStates() ?: emptyList() }
  }

  suspend fun itemSelected(itemData: SeItemData, modifiers: Int, searchText: String): Boolean {
    val provider = providers[itemData.providerId] ?: return false
    return provider.itemSelected(itemData, modifiers, searchText)
  }

  override fun dispose() {}

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
      val hasWildcard = allProviderIds.any { it.isWildcard }

      val localProviders =
        if (forceRemote) emptyMap()
        else SeItemsProviderFactory.EP_NAME.extensionList.asFlow().filter {
          hasWildcard || allProviderIds.contains(SeProviderId(it.id))
        }.mapNotNull {
          try {
            it.getItemsProvider(project, dataContext)
          }
          catch (e: Exception) {
            LOG.warn("SearchEverywhere item provider wasn't created. Exception: ${e.message}")
            null
          }
        }.toList().associate { provider ->
          SeProviderId(provider.id) to SeLocalItemDataProvider(provider, sessionRef)
        }

      val remoteProviderIds =
        if (hasWildcard) SeRemoteApi.getInstance().getAvailableProviderIds()
        else allProviderIds - localProviders.keys.toSet()

      val remoteProviderIdToName =
        SeRemoteApi.getInstance().getDisplayNameForProviders(project.projectId(), sessionRef, dataContextId, remoteProviderIds.toList())

      val frontendProviders = remoteProviderIdToName.map { (providerId, name) ->
        SeFrontendItemDataProvider(project.projectId(), providerId, name, sessionRef, dataContextId)
      }.associateBy { it.id }

      val providers = frontendProviders + localProviders
      val delegate = SeTabDelegate(project, logLabel, providers)
      providers.values.forEach { Disposer.register(delegate, it) }

      return delegate
    }
  }
}