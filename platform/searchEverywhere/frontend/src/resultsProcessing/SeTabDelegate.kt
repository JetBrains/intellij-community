// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.resultsProcessing

import com.intellij.ide.rpc.rpcId
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.project.projectId
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.frontend.SeFrontendItemDataProvider
import com.intellij.platform.searchEverywhere.utils.initAsync
import com.intellij.platform.searchEverywhere.impl.SeRemoteApi
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.SeLog.ITEM_EMIT
import com.intellij.platform.searchEverywhere.providers.SeProvidersHolder
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import fleet.kernel.DurableRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

@OptIn(ExperimentalCoroutinesApi::class)
@Internal
class SeTabDelegate(
  val project: Project?,
  private val sessionRef: DurableRef<SeSessionEntity>,
  private val logLabel: String,
  private val providerIds: List<SeProviderId>,
  private val initEvent: AnActionEvent,
  val scope: CoroutineScope
) : Disposable {
  private val providers = initAsync(scope) { initializeProviders(project, providerIds, initEvent, sessionRef, logLabel, this) }
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

    val presentation = itemData.presentation
    if (presentation is SeActionItemPresentation) {
      withContext(Dispatchers.EDT) {
        // TODO: We have to find a way to keep the presentation immutable and still toggle the switch in UI
        presentation.commonData.toggleStateIfSwitcher()
      }
    }

    return provider.itemSelected(itemData, modifiers, searchText)
  }

  fun getProvidersIds(): List<SeProviderId> = providerIds

  /**
   * Defines if results can be shown in <i>Find</i> toolwindow.
   */
  suspend fun canBeShownInFindResults(): Boolean {
    return providers.getValue().values.any { it.canBeShownInFindResults() }
  }

  override fun dispose() {}

  companion object {
    private suspend fun initializeProviders(
      project: Project?,
      providerIds: List<SeProviderId>,
      initEvent: AnActionEvent,
      sessionRef: DurableRef<SeSessionEntity>,
      logLabel: String,
      parentDisposable: Disposable,
    ): Map<SeProviderId, SeItemDataProvider> {
      val dataContextId = readAction {
        initEvent.dataContext.rpcId()
      }

      val hasWildcard = providerIds.any { it.isWildcard }
      val remoteProviderIds = SeRemoteApi.getInstance().getAvailableProviderIds().filter { hasWildcard || providerIds.contains(it) }.toSet()
      val localFactories = SeItemsProviderFactory.EP_NAME.extensionList.associateBy { SeProviderId(it.id) }

      // If we have it on BE, we use the BE provider.
      // This is needed because extensions are available on both sides in the monolith (BE and FE)
      // even if the extension was registered on BE only.
      // It's better to treat FE provider as BE in monolith than treat BE provider as FE in split mode.
      val localProviderIds =
        (if (hasWildcard) localFactories.keys else providerIds) - remoteProviderIds

      val localProvidersHolder = SeProvidersHolder.initialize(initEvent, project, sessionRef, logLabel, localProviderIds)
      val localProviders = localProviderIds.mapNotNull { providerId ->
        localProvidersHolder.get(providerId, !hasWildcard)?.let {
          providerId to it
        }
      }.toMap()

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