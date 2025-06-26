// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.resultsProcessing

import com.intellij.ide.rpc.rpcId
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.equalityProviders.SeEqualityChecker
import com.intellij.platform.searchEverywhere.frontend.SeFrontendItemDataProvidersFacade
import com.intellij.platform.searchEverywhere.frontend.SeFrontendService
import com.intellij.platform.searchEverywhere.impl.SeRemoteApi
import com.intellij.platform.searchEverywhere.providers.SeLocalItemDataProvider
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.SeLog.ITEM_EMIT
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import com.intellij.platform.searchEverywhere.utils.initAsync
import fleet.kernel.DurableRef
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
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
  val scope: CoroutineScope,
) : Disposable {
  private val providers = initAsync(scope) {
    initializeProviders(project, providerIds, initEvent, sessionRef)
  }
  private val providersAndLimits = providerIds.associateWith { Int.MAX_VALUE }

  suspend fun getProvidersIdToName(): Map<SeProviderId, @Nls String> = providers.getValue().getProvidersIdToName()

  fun getItems(params: SeParams, disabledProviders: List<SeProviderId>? = null): Flow<SeResultEvent> {
    val accumulator = SeResultsAccumulator(providersAndLimits)

    return flow {
      providers.getValue().getItems(params, disabledProviders ?: emptyList()) {
        SeLog.log(ITEM_EMIT) { "Tab delegate for ${logLabel} emits: ${it.uuid} - ${it.presentation.text}" }
        accumulator.add(it)
      }.collect {
        emit(it)
      }
    }
  }

  suspend fun getSearchScopesInfos(): List<SeSearchScopesInfo> {
    return providers.getValue().getSearchScopesInfos()
  }

  suspend fun getTypeVisibilityStates(index: Int = 0): List<SeTypeVisibilityStatePresentation> {
    return providers.getValue().getTypeVisibilityStates(index)
  }

  suspend fun itemSelected(itemData: SeItemData, modifiers: Int, searchText: String): Boolean {
    return providers.getValue().itemSelected(itemData, modifiers, searchText)
  }

  fun getProvidersIds(): List<SeProviderId> = providerIds

  /**
   * Defines if results can be shown in <i>Find</i> toolwindow.
   */
  suspend fun canBeShownInFindResults(): Boolean {
    return providers.getValue().canBeShownInFindResults()
  }

  suspend fun openInFindToolWindow(
    sessionRef: DurableRef<SeSessionEntity>,
    params: SeParams,
    initEvent: AnActionEvent,
    isAllTab: Boolean,
    disabledProviders: List<SeProviderId>? = null
  ): Boolean {
    if (project == null) return false

    val dataContextId = readAction {
      initEvent.dataContext.rpcId()
    }
    return SeRemoteApi.getInstance().openInFindToolWindow(project.projectId(),
                                                          sessionRef,
                                                          dataContextId,
                                                          providers.getValue().getProviderIds(disabledProviders ?: emptyList()),
                                                          params,
                                                          isAllTab)
  }

  override fun dispose() {}

  private class Providers(
    private val localProviders: Map<SeProviderId, SeLocalItemDataProvider>,
    private val frontendProvidersFacade: SeFrontendItemDataProvidersFacade?,
  ) {

    fun getProvidersIdToName(): Map<SeProviderId, @Nls String> = localProviders.mapValues { it.value.displayName } +
                                                                 (frontendProvidersFacade?.idsWithDisplayNames ?: emptyMap())

    suspend fun getSearchScopesInfos(): List<SeSearchScopesInfo> {
      return localProviders.values.mapNotNull { it.getSearchScopesInfo() } +
             (frontendProvidersFacade?.getSearchScopesInfos()?.values ?: emptyList())
    }

    suspend fun canBeShownInFindResults(): Boolean {
      return localProviders.values.any { it.canBeShownInFindResults() } || frontendProvidersFacade?.canBeShownInFindResults() == true
    }

    suspend fun getTypeVisibilityStates(index: Int = 0): List<SeTypeVisibilityStatePresentation> {
      return localProviders.values.flatMap { it.getTypeVisibilityStates(index) ?: emptyList() } +
             (frontendProvidersFacade?.getTypeVisibilityStates(index) ?: emptyList())
    }

    fun getItems(params: SeParams, disabledProviders: List<SeProviderId>, mapToResultEvent: suspend (SeItemData) -> SeResultEvent?): Flow<SeResultEvent> {
      return channelFlow {
        launch {
          val equalityChecker = SeEqualityChecker()
          val localProviders = localProviders.filterKeys { !disabledProviders.contains(it) }.values

          localProviders.asFlow().flatMapMerge { provider ->
            provider.getItems(params)
          }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND).mapNotNull {
            val checkedItemData = equalityChecker.checkAndUpdateIfNeeded(it) ?: return@mapNotNull null
            mapToResultEvent(checkedItemData)
          }.collect {
            send(it)
          }
        }

        if (frontendProvidersFacade != null) {
          launch {
            frontendProvidersFacade.getItems(params, disabledProviders).mapNotNull {
              mapToResultEvent(it)
            }.collect {
              send(it)
            }
          }
        }
      }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)
    }

    suspend fun itemSelected(itemData: SeItemData, modifiers: Int, searchText: String): Boolean {
      if (!localProviders.keys.contains(itemData.providerId) &&
          !(frontendProvidersFacade?.hasId(itemData.providerId) ?: false)) return false

      val presentation = itemData.presentation
      if (presentation is SeActionItemPresentation) {
        withContext(Dispatchers.EDT) {
          // TODO: We have to find a way to keep the presentation immutable and still toggle the switch in UI
          presentation.commonData.toggleStateIfSwitcher()
        }
      }

      return localProviders[itemData.providerId]?.itemSelected(itemData, modifiers, searchText) ?: run {
        frontendProvidersFacade?.itemSelected(itemData, modifiers, searchText) ?: false
      }
    }

    fun getProviderIds(
      disabledProviders: List<SeProviderId>,
    ): List<SeProviderId> {
      val localProviders = localProviders.filterKeys { !disabledProviders.contains(it) }.keys
      val frontedProviders = frontendProvidersFacade?.providerIds?.filter { !disabledProviders.contains(it) }
                             ?: emptyList()
      return localProviders.toList() + frontedProviders
    }
  }

  companion object {
    suspend fun shouldShowLegacyContributorInSeparateTab(
      project: Project,
      providerId: SeProviderId,
      initEvent: AnActionEvent,
      sessionRef: DurableRef<SeSessionEntity>,
    ): Boolean {
      val dataContextId = readAction {
        initEvent.dataContext.rpcId()
      }
      return SeFrontendService.getInstance(project).localProvidersHolder?.getLegacyContributor(providerId, false)?.isShownInSeparateTab == true ||
             SeRemoteApi.getInstance().isShownInSeparateTab(project.projectId(), sessionRef, dataContextId, providerId)
    }

    private suspend fun initializeProviders(
      project: Project?,
      providerIds: List<SeProviderId>,
      initEvent: AnActionEvent,
      sessionRef: DurableRef<SeSessionEntity>,
    ): Providers {
      val hasWildcard = providerIds.any { it.isWildcard }
      val remoteProviderIds = SeRemoteApi.getInstance().getAvailableProviderIds().filter { hasWildcard || providerIds.contains(it) }.toSet()
      val localFactories = SeItemsProviderFactory.EP_NAME.extensionList.associateBy { SeProviderId(it.id) }

      // If we have it on BE, we use the BE provider.
      // This is needed because extensions are available on both sides in the monolith (BE and FE)
      // even if the extension was registered on BE only.
      // It's better to treat FE provider as BE in monolith than treat BE provider as FE in split mode.
      val localProviderIds =
        (if (hasWildcard) localFactories.keys else providerIds) - remoteProviderIds

      val localProvidersHolder = SeFrontendService.getInstance(project).localProvidersHolder
                                 ?: error("Local providers holder is not initialized")
      val localProviders = localProviderIds.mapNotNull { providerId ->
        localProvidersHolder.get(providerId, hasWildcard)?.let {
          providerId to it
        }
      }.toMap()

      val dataContextId = readAction {
        initEvent.dataContext.rpcId()
      }

      val frontendProvidersFacade = if (project != null) {
        val remoteProviderIdToName =
          SeRemoteApi.getInstance().getDisplayNameForProviders(project.projectId(), sessionRef, dataContextId, remoteProviderIds.toList())

        if (remoteProviderIdToName.isEmpty()) null
        else SeFrontendItemDataProvidersFacade(project.projectId(), remoteProviderIdToName, sessionRef, dataContextId, hasWildcard)
      }
      else null

      return Providers(localProviders, frontendProvidersFacade)
    }
  }
}