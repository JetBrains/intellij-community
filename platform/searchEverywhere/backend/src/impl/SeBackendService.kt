// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.ide.rpc.DataContextId
import com.intellij.ide.rpc.dataContext
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.project.ProjectId
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.equalityProviders.SeEqualityChecker
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.SeProvidersHolder
import com.intellij.platform.searchEverywhere.providers.SeSortedProviderIds
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import com.intellij.platform.searchEverywhere.utils.SeResultsCountBalancer
import com.jetbrains.rhizomedb.EID
import fleet.kernel.onDispose
import fleet.kernel.rete.Rete
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.any

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class SeBackendService(val project: Project, private val coroutineScope: CoroutineScope) {
  private val sessionIdToProviderHolders: ConcurrentHashMap<EID, SeProvidersHolder> = ConcurrentHashMap()
  private val mutex: Mutex = Mutex()

  @OptIn(ExperimentalCoroutinesApi::class)
  suspend fun getItems(
    session: SeSession,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
    params: SeParams,
    dataContextId: DataContextId?,
    requestedCountChannel: ReceiveChannel<Int>,
  ): Flow<SeTransferEvent> {
    val providerHolder = getProvidersHolder(session, dataContextId) ?: return emptyFlow()

    val requestedCountState = MutableStateFlow(0)
    val receivingJob = coroutineScope.launch {
      requestedCountChannel.consumeEach { count ->
        requestedCountState.update { it + count }
      }
    }

    val sortedProviderIds = SeSortedProviderIds.create(providerIds, providerHolder, session)
    val resultsBalancer = SeResultsCountBalancer("BE",
                                                 nonBlockedProviderIds = emptyList(),
                                                 highPriorityProviderIds = sortedProviderIds.essential,
                                                 lowPriorityProviderIds = sortedProviderIds.nonEssential)

    SeLog.log(SeLog.ITEM_EMIT) { "Backend will request items from providers: ${providerIds.joinToString(", ")}" }

    val itemsFlows = providerIds.mapNotNull { providerId ->
      providerHolder
        .get(providerId, isAllTab)
        ?.getItems(params)
        ?.map {
          resultsBalancer.add(it)
          SeTransferItem(it) as SeTransferEvent
        }
        ?.onCompletion {
          resultsBalancer.end(providerId)
          emit(SeTransferEnd(providerId))
        }
    }

    val equalityChecker = SeEqualityChecker()
    return flow {
      itemsFlows.merge().buffer(capacity = 0, onBufferOverflow = BufferOverflow.SUSPEND).mapNotNull { transferEvent ->
        when (transferEvent) {
          is SeTransferEnd -> transferEvent
          is SeTransferItem -> equalityChecker.checkAndUpdateIfNeeded(transferEvent.itemData)?.let { SeTransferItem(it) }
        }
      }.collect { item ->
        requestedCountState.first { it > 0 }
        requestedCountState.update { it - 1 }

        emit(item)
      }
    }.onCompletion {
      SeLog.log(SeLog.ITEM_EMIT) { "Backend merged flow completed" }
      receivingJob.cancel()
    }
  }

  suspend fun getAvailableProviderIds(
    session: SeSession,
    dataContextId: DataContextId,
  ): SeSortedProviderIds? {
    val providersHolder = getProvidersHolder(session, dataContextId) ?: return null
    val allProviderIds = SeItemsProviderFactory.EP_NAME.extensionList.map { it.id.toProviderId() } + providersHolder.legacyAllTabContributors.map { it.key }
    return SeSortedProviderIds.create(allProviderIds, providersHolder, session)
  }

  fun tryGetProvider(id: SeProviderId, isAllTab: Boolean, session: SeSession): SeItemsProvider? {
    return session.asRef().derefOrNull()?.eid?.let {
      sessionIdToProviderHolders[it]?.get(id, isAllTab)?.provider
    }
  }

  private suspend fun getProvidersHolder(
    session: SeSession,
    dataContextId: DataContextId?,
  ): SeProvidersHolder? =
    mutex.withLock {
      val sessionEntity = session.asRef().derefOrNull() ?: return@withLock null
      sessionIdToProviderHolders[sessionEntity.eid]?.let { return@withLock it }

      if (dataContextId == null) {
        SeLog.error("Cannot create providers on the backend: no serialized data context")
        return@withLock null
      }

      val dataContext = withContext(Dispatchers.EDT) {
        dataContextId.dataContext()
      } ?: run {
        SeLog.error("Cannot create providers on the backend: couldn't deserialize data context")
        return@withLock null
      }

      val actionEvent = AnActionEvent.createEvent(dataContext, null, "", ActionUiKind.NONE, null)
      val providersHolder = SeProvidersHolder.initialize(actionEvent, project, session, "Backend", true)
      sessionIdToProviderHolders[sessionEntity.eid] = providersHolder

      sessionEntity.onDispose(coroutineScope.coroutineContext[Rete]!!) {
        Disposer.dispose(providersHolder)
        sessionIdToProviderHolders.remove(sessionEntity.eid)
      }

      return@withLock providersHolder
    }

  suspend fun itemSelected(session: SeSession, itemData: SeItemData, modifiers: Int, searchText: String, isAllTab: Boolean): Boolean {
    val provider = getProvidersHolder(session, null)?.get(itemData.providerId, isAllTab) ?: return false

    return provider.itemSelected(itemData, modifiers, searchText)
  }

  suspend fun getSearchScopesInfoForProviders(
    session: SeSession,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
  ): Map<SeProviderId, SearchScopesInfo> {
    return providerIds.mapNotNull { providerId ->
      val provider = getProvidersHolder(session, dataContextId)?.get(providerId, isAllTab)
      provider?.getSearchScopesInfo()?.let {
        providerId to it
      }
    }.toMap()
  }

  suspend fun getTypeVisibilityStatesForProviders(
    index: Int,
    session: SeSession,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
  ): List<SeTypeVisibilityStatePresentation> {
    return providerIds.mapNotNull { providerId ->
      val provider = getProvidersHolder(session, dataContextId)?.get(providerId, isAllTab)
      provider?.getTypeVisibilityStates(index)
    }.flatten()
  }

  suspend fun getDisplayNameForProvider(
    session: SeSession,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
  ): Map<SeProviderId, @Nls String> {
    val providersHolder = getProvidersHolder(session, dataContextId) ?: return emptyMap()
    return providerIds.mapNotNull { id ->
      providersHolder.get(id, true)?.displayName?.let { displayName ->
        id to displayName
      }
    }.toMap()
  }

  suspend fun canBeShownInFindResults(
    session: SeSession,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
  ): Boolean {
    return providerIds.any { providerId ->
      val provider = getProvidersHolder(session, dataContextId)?.get(providerId, isAllTab)
      provider?.canBeShownInFindResults() ?: false
    }
  }

  suspend fun isShownInSeparateTab(
    session: SeSession,
    dataContextId: DataContextId,
    providerId: SeProviderId,
  ): Boolean {
    return getProvidersHolder(session, dataContextId)?.getLegacyContributor(providerId, false)?.isShownInSeparateTab ?: false
  }

  suspend fun openInFindToolWindow(
    projectId: ProjectId,
    session: SeSession,
    dataContextId: DataContextId?,
    providerIds: List<SeProviderId>,
    params: SeParams,
    isAllTab: Boolean,
  ): Boolean {
    val providersHolder = getProvidersHolder(session, dataContextId)
    if (providersHolder == null) return false

    SeFindToolWindowManager(project).openInFindToolWindow(
      providerIds, params, isAllTab, providersHolder, projectId,
    )
    return true
  }

  suspend fun getUpdatedPresentation(item: SeItemData): SeItemPresentation? {
    return item.fetchItemIfExists()?.presentation()
  }

  suspend fun performExtendedAction(session: SeSession, itemData: SeItemData, isAllTab: Boolean): Boolean {
    val provider = getProvidersHolder(session, null)?.get(itemData.providerId, isAllTab) ?: return false
    return provider.performExtendedAction(itemData)
  }

  suspend fun getPreviewInfo(
    session: SeSession,
    itemData: SeItemData,
    isAllTab: Boolean, project: Project,
  ): SePreviewInfo? {
    val provider = getProvidersHolder(session, null)?.get(itemData.providerId, isAllTab) ?: return null
    return provider.getPreviewInfo(itemData, project)
  }

  suspend fun isPreviewEnabled(
    session: SeSession,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
  ): Boolean {
    return providerIds.any { providerId ->
      val provider = getProvidersHolder(session, dataContextId)?.get(providerId, isAllTab)
      provider?.isPreviewEnabled() ?: false
    }
  }

  suspend fun isExtendedInfoEnabled(
    session: SeSession,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
  ): Boolean {
    return providerIds.any { providerId ->
      val provider = getProvidersHolder(session, dataContextId)?.get(providerId, isAllTab)
      provider?.isExtendedInfoEnabled() ?: false
    }
  }

  suspend fun isCommandsSupported(
    session: SeSession,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
  ): Boolean {
    return providerIds.any { providerId ->
      val provider = getProvidersHolder(session, dataContextId)?.get(providerId, isAllTab)
      provider?.isCommandsSupported() ?: false
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SeBackendService = project.service<SeBackendService>()
  }
}