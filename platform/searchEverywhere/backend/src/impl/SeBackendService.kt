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
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.equalityProviders.SeEqualityChecker
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.SeProvidersHolder
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import com.jetbrains.rhizomedb.EID
import fleet.kernel.DurableRef
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

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class SeBackendService(val project: Project, private val coroutineScope: CoroutineScope) {
  private val sessionIdToProviderHolders: MutableMap<EID, SeProvidersHolder> = HashMap()
  private val mutex: Mutex = Mutex()

  @OptIn(ExperimentalCoroutinesApi::class)
  suspend fun getItems(
    sessionRef: DurableRef<SeSessionEntity>,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
    params: SeParams,
    dataContextId: DataContextId?,
    requestedCountChannel: ReceiveChannel<Int>,
  ): Flow<SeItemData> {
    val requestedCountState = MutableStateFlow(0)
    val receivingJob = coroutineScope.launch {
      requestedCountChannel.consumeEach { count ->
        requestedCountState.update { it + count }
      }
    }

    SeLog.log(SeLog.ITEM_EMIT) { "Backend will request items from providers: ${providerIds.joinToString(", ")}" }

    val itemsFlows = providerIds.mapNotNull {
      getProvidersHolder(sessionRef, dataContextId)
        ?.get(it, isAllTab)
        ?.getItems(params)
    }

    val equalityChecker = SeEqualityChecker()
    return flow {
      itemsFlows.merge().buffer(capacity = 0, onBufferOverflow = BufferOverflow.SUSPEND).mapNotNull { itemData ->
        equalityChecker.checkAndUpdateIfNeeded(itemData)
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

  private suspend fun getProvidersHolder(
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId?,
  ): SeProvidersHolder? =
    mutex.withLock {
      val session = sessionRef.derefOrNull() ?: return@withLock null
      sessionIdToProviderHolders[session.eid]?.let { return@withLock it }

      if (dataContextId == null) {
        throw IllegalStateException("Cannot create providers on the backend: no serialized data context")
      }

      val dataContext = withContext(Dispatchers.EDT) {
        dataContextId.dataContext()
      } ?: throw IllegalStateException("Cannot create providers on the backend: couldn't deserialize data context")

      val actionEvent = AnActionEvent.createEvent(dataContext, null, "", ActionUiKind.NONE, null)
      val providersHolder = SeProvidersHolder.initialize(actionEvent, project, sessionRef, "Backend")
      sessionIdToProviderHolders[session.eid] = providersHolder

      session.onDispose(coroutineScope.coroutineContext[Rete]!!) {
        Disposer.dispose(providersHolder)
        sessionIdToProviderHolders.remove(session.eid)
      }

      return@withLock providersHolder
    }

  suspend fun itemSelected(sessionRef: DurableRef<SeSessionEntity>, itemData: SeItemData, modifiers: Int, searchText: String, isAllTab: Boolean): Boolean {
    val provider = getProvidersHolder(sessionRef, null)?.get(itemData.providerId, isAllTab) ?: return false

    return provider.itemSelected(itemData, modifiers, searchText)
  }

  suspend fun getSearchScopesInfoForProviders(
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
  ): Map<SeProviderId, SeSearchScopesInfo> {
    return providerIds.mapNotNull { providerId ->
      val provider = getProvidersHolder(sessionRef, dataContextId)?.get(providerId, isAllTab)
      provider?.getSearchScopesInfo()?.let {
        providerId to it
      }
    }.toMap()
  }

  suspend fun getTypeVisibilityStatesForProviders(
    index: Int,
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
  ): List<SeTypeVisibilityStatePresentation> {
    return providerIds.mapNotNull { providerId ->
      val provider = getProvidersHolder(sessionRef, dataContextId)?.get(providerId, isAllTab)
      provider?.getTypeVisibilityStates(index)
    }.flatten()
  }

  suspend fun getDisplayNameForProvider(
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
  ): Map<SeProviderId, @Nls String> {
    val providersHolder = getProvidersHolder(sessionRef, dataContextId) ?: return emptyMap()
    return providerIds.mapNotNull { id ->
      providersHolder.get(id, true)?.displayName?.let { displayName ->
        id to displayName
      }
    }.toMap()
  }

  suspend fun canBeShownInFindResults(
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
  ): Boolean {
    return providerIds.any { providerId ->
      val provider = getProvidersHolder(sessionRef, dataContextId)?.get(providerId, isAllTab)
      provider?.canBeShownInFindResults() ?: false
    }
  }

  suspend fun isShownInSeparateTab(
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerId: SeProviderId,
  ): Boolean {
    return getProvidersHolder(sessionRef, dataContextId)?.getLegacyContributor(providerId, false)?.isShownInSeparateTab ?: false
  }

  suspend fun openInFindToolWindow(
    projectId: ProjectId,
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId?,
    providerIds: List<SeProviderId>,
    params: SeParams,
    isAllTab: Boolean,
  ): Boolean {
    val providersHolder = getProvidersHolder(sessionRef, dataContextId)
    if (providersHolder == null) return false

    SeFindToolWindowManager(project).openInFindToolWindow(
      providerIds, params, isAllTab, providersHolder, projectId,
    )
    return true
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SeBackendService = project.service<SeBackendService>()
  }
}