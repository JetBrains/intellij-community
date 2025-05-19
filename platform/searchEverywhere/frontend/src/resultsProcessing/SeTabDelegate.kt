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
import com.intellij.platform.searchEverywhere.frontend.utils.suspendLazy
import com.intellij.platform.searchEverywhere.impl.SeRemoteApi
import com.intellij.platform.searchEverywhere.providers.SeLocalItemDataProvider
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
class SeTabDelegate(val project: Project?,
                    private val sessionRef: DurableRef<SeSessionEntity>,
                    private val logLabel: String,
                    private val providerIds: List<SeProviderId>,
                    private val dataContext: DataContext): Disposable {
  private val providers = suspendLazy { initializeProviders(project, providerIds, dataContext, sessionRef, this) }
  private val providersAndLimits = providerIds.associateWith { Int.MAX_VALUE }

  suspend fun getProvidersIdToName(): Map<SeProviderId, @Nls String> = providers.getValue().mapValues { it.value.displayName }

  fun getItems(params: SeParams, disabledProviders: List<SeProviderId>? = null): Flow<SeResultEvent> {
    val accumulator = SeResultsAccumulator(providersAndLimits)

    return flow {
      val providers = providers.getValue()
      val enabledProviders = (disabledProviders?.let { providers.filterKeys { it !in disabledProviders } } ?: providers).values

      enabledProviders.asFlow().flatMapMerge { provider ->
        provider.getItems(params).mapNotNull {
          SeLog.log(ITEM_EMIT) { "Tab delegate for ${logLabel} emits: ${it.presentation.text}" }
          accumulator.add(it)
        }
      }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND).collect {
        emit(it)
      }
    }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)
  }

  suspend fun getSearchScopesInfos(): List<SeSearchScopesInfo> {
    return providers.getValue().values.mapNotNull { it.getSearchScopesInfo() }
  }

  suspend fun getTypeVisibilityStates(): List<SeTypeVisibilityStatePresentation> {
    return providers.getValue().values.flatMap { it.getTypeVisibilityStates() ?: emptyList() }
  }

  suspend fun itemSelected(itemData: SeItemData, modifiers: Int, searchText: String): Boolean {
    val provider = providers.getValue()[itemData.providerId] ?: return false
    return provider.itemSelected(itemData, modifiers, searchText)
  }

  fun getProvidersIds() : List<SeProviderId> = providerIds

  /**
   * Defines if results can be shown in <i>Find</i> toolwindow.
   */
  suspend fun canBeShownInFindResults(): Boolean {
    return providers.getValue().values.any { it.canBeShownInFindResults() }
  }

  override fun dispose() {}

  companion object {
    private val LOG = Logger.getInstance(SeTabDelegate::class.java)

    private suspend fun initializeProviders(
      project: Project?,
      providerIds: List<SeProviderId>,
      dataContext: DataContext,
      sessionRef: DurableRef<SeSessionEntity>,
      parentDisposable: Disposable,
    ): Map<SeProviderId, SeItemDataProvider> {
      val dataContextId = readAction {
        dataContext.rpcId()
      }

      val allProviderIds = providerIds.toSet()
      val hasWildcard = allProviderIds.any { it.isWildcard }

      val localProviders =
        SeItemsProviderFactory.EP_NAME.extensionList.asFlow().filter {
          hasWildcard || allProviderIds.contains(SeProviderId(it.id))
        }.mapNotNull {
          try {
            val provider = it.getItemsProvider(project, dataContext)
            if (provider == null) {
              LOG.info("SearchEverywhere items provider factory returned null: ${it.id}")
            }
            provider
          }
          catch (e: Exception) {
            LOG.warn("SearchEverywhere items provider wasn't created: ${it.id}. Exception:\n${e.message}")
            null
          }
        }.toList().associate { provider ->
          SeProviderId(provider.id) to SeLocalItemDataProvider(provider, sessionRef)
        }

      val remoteProviderIds =
        if (hasWildcard) SeRemoteApi.getInstance().getAvailableProviderIds()
        else allProviderIds - localProviders.keys.toSet()

      val frontendProviders = if (project != null) {
        val remoteProviderIdToName =
          SeRemoteApi.getInstance().getDisplayNameForProviders(project.projectId(), sessionRef, dataContextId, remoteProviderIds.toList())

        remoteProviderIdToName.map { (providerId, name) ->
          SeFrontendItemDataProvider(project.projectId(), providerId, name, sessionRef, dataContextId)
        }.associateBy { it.id }
      }
      else emptyMap()

      val providers = frontendProviders + localProviders
      providers.values.forEach { Disposer.register(parentDisposable, it) }

      return providers
    }
  }
}