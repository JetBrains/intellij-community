// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.resultsProcessing

import com.intellij.ide.rpc.rpcId
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.equalityProviders.SeEqualityChecker
import com.intellij.platform.searchEverywhere.frontend.SeFrontendItemDataProvidersFacade
import com.intellij.platform.searchEverywhere.frontend.SeFrontendOnlyItemsProviderFactory
import com.intellij.platform.searchEverywhere.frontend.SeFrontendService
import com.intellij.platform.searchEverywhere.impl.SeRemoteApi
import com.intellij.platform.searchEverywhere.providers.SeLocalItemDataProvider
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.SeLog.ITEM_EMIT
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import com.intellij.platform.searchEverywhere.utils.SeResultsCountBalancer
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
    initializeProviders(project, providerIds, initEvent, sessionRef, logLabel)
  }
  suspend fun getProvidersIdToName(): Map<SeProviderId, @Nls String> = providers.getValue().getProvidersIdToName()

  fun getItems(params: SeParams, disabledProviders: List<SeProviderId>? = null): Flow<SeResultEvent> {
    val disabledProviders = fixDisabledProviders(disabledProviders)

    return flow {
      val initializedProviders = providers.getValue()

      val remoteProviderIds = initializedProviders.getRemoteProviderIds()
      val allEssentialProviderIds = initializedProviders.essentialProviderIds
      val localProviders = initializedProviders.getLocalProviderIds().toSet()
      val localEssentialProviders = allEssentialProviderIds.intersect(localProviders)
      val localNonEssentialProviders = localProviders.subtract(allEssentialProviderIds)

      // We shouldn't block remoteProviderIds because they may miss some results after equality check on the Backend
      val balancer = SeResultsCountBalancer("FE",
                                            nonBlockedProviderIds = remoteProviderIds,
                                            highPriorityProviderIds = localEssentialProviders,
                                            lowPriorityProviderIds = localNonEssentialProviders)

      val accumulator = SeResultsAccumulator()

      disabledProviders?.forEach {
        balancer.end(it)
        emit(SeResultEndEvent(it))
      }

      providers.getValue().getItems(params, disabledProviders ?: emptyList()) { equalityChecker, transferEvent ->
        when (transferEvent) {
          is SeTransferEnd -> {
            SeLog.log(ITEM_EMIT) { "Tab delegate for ${logLabel} ends: ${transferEvent.providerId.value}" }
            balancer.end(transferEvent.providerId)
            SeResultEndEvent(transferEvent.providerId)
          }
          is SeTransferItem -> {
            val itemData = transferEvent.itemData
            balancer.add(itemData)

            val checkedItemData = if (equalityChecker != null) {
              equalityChecker.checkAndUpdateIfNeeded(itemData)
            }
            else itemData

            checkedItemData?.let {
              SeLog.log(ITEM_EMIT) { "Tab delegate for ${logLabel} emits: ${checkedItemData.uuid} - ${checkedItemData.presentation.text}" }
              accumulator.add(checkedItemData)
            }
          }
        }
      }.collect {
        emit(it)
      }
    }
  }

  suspend fun getSearchScopesInfos(): List<SearchScopesInfo> {
    return providers.getValue().getSearchScopesInfos()
  }

  suspend fun getTypeVisibilityStates(index: Int = 0): List<SeTypeVisibilityStatePresentation> {
    return providers.getValue().getTypeVisibilityStates(index)
  }

  suspend fun itemSelected(itemData: SeItemData, modifiers: Int, searchText: String): Boolean {
    return providers.getValue().itemSelected(itemData, modifiers, searchText)
  }

  fun getProvidersIds(): List<SeProviderId> = providerIds

  suspend fun essentialProviderIds(): Set<SeProviderId> = providers.getValue().essentialProviderIds

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
    val essentialProviderIds: Set<SeProviderId>,
  ) {
    fun getProvidersIdToName(): Map<SeProviderId, @Nls String> = localProviders.mapValues { it.value.displayName } +
                                                                 (frontendProvidersFacade?.idsWithDisplayNames ?: emptyMap())

    suspend fun getSearchScopesInfos(): List<SearchScopesInfo> {
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

    fun getLocalProviderIds(): List<SeProviderId> = localProviders.keys.toList()
    fun getRemoteProviderIds(): List<SeProviderId> = frontendProvidersFacade?.providerIds ?: emptyList()

    fun getItems(params: SeParams, disabledProviders: List<SeProviderId>, mapToResultEvent: suspend (SeEqualityChecker?, SeTransferEvent) -> SeResultEvent?): Flow<SeResultEvent> {
      return channelFlow {
        launch {
          val equalityChecker = SeEqualityChecker()
          val localProviders = localProviders.filterKeys { !disabledProviders.contains(it) }.values

          localProviders.asFlow().flatMapMerge { provider ->
            provider.getItems(params).map {
              SeTransferItem(it) as SeTransferEvent
            }.onCompletion {
              emit(SeTransferEnd(provider.id))
            }
          }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND).mapNotNull { transferEvent ->
            mapToResultEvent(equalityChecker, transferEvent)
          }.collect {
            send(it)
          }
        }

        if (frontendProvidersFacade != null) {
          launch {
            frontendProvidersFacade.getItems(params, disabledProviders).mapNotNull {
              mapToResultEvent(null, it)
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

  // Workaround for: IJPL-188383 Search Everywhere, All tab: 'Top Hit' filter is duplicated
  // Add/remove Top Hit (On Client) according to the presence of Top Hit provider
  private fun fixDisabledProviders(disabledProviders: List<SeProviderId>?): List<SeProviderId>? {
    val all = disabledProviders?.toMutableSet() ?: return null

    val topHitClientId = SeProviderIdUtils.TOP_HIT_ID.toProviderId()
    if (all.contains(SeProviderIdUtils.TOP_HIT_HOST_ID.toProviderId())) {
      all.add(topHitClientId)
    }
    else all.remove(topHitClientId)

    return all.toList()
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
      logLabel: String,
    ): Providers {
      val projectId = project?.projectId()
      val dataContextId = readAction {
        initEvent.dataContext.rpcId()
      }

      val hasWildcard = providerIds.any { it.isWildcard }

      val localFactories = SeItemsProviderFactory.EP_NAME.extensionList.associateBy { SeProviderId(it.id) }
      val frontendOnlyIds = localFactories.filter { it.value is SeFrontendOnlyItemsProviderFactory }.map { it.key }.toSet()

      val availableRemoteProviders = if (projectId != null) SeRemoteApi.getInstance().getAvailableProviderIds(projectId, sessionRef, dataContextId) else emptyMap()

      val essentialRemoteProviderIds = availableRemoteProviders[SeProviderIdUtils.ESSENTIAL_KEY]?.filter {
        !frontendOnlyIds.contains(it)
      }?.toSet() ?: emptySet()

      val nonEssentialRemoteProviderIds = availableRemoteProviders[SeProviderIdUtils.NON_ESSENTIAL_KEY]?.filter {
        !frontendOnlyIds.contains(it)
      }?.toSet() ?: emptySet()

      val remoteProviderIds = essentialRemoteProviderIds.union(nonEssentialRemoteProviderIds).filter { hasWildcard || providerIds.contains(it) }.toSet()

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

      val frontendProvidersFacade = if (project != null) {
        val remoteProviderIdToName =
          SeRemoteApi.getInstance().getDisplayNameForProviders(project.projectId(), sessionRef, dataContextId, remoteProviderIds.toList())

        if (remoteProviderIdToName.isEmpty()) null
        else SeFrontendItemDataProvidersFacade(project.projectId(),
                                               remoteProviderIdToName,
                                               sessionRef,
                                               dataContextId,
                                               hasWildcard,
                                               essentialRemoteProviderIds.filter { remoteProviderIdToName.containsKey(it) }.toSet())
      }
      else null

      val allEssentials = localProvidersHolder.getEssentialAllTabProviderIds().filter { localProviders[it] != null }.toSet() +
                          (frontendProvidersFacade?.essentialProviderIds ?: emptySet())

      SeLog.log(SeLog.THROTTLING) { "Essential contributors for $logLabel tab : " + allEssentials.joinToString(", ") { it.value } }
      return Providers(localProviders, frontendProvidersFacade, allEssentials)
    }
  }
}