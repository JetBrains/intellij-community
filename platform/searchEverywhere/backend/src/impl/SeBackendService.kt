// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.ide.rpc.DataContextId
import com.intellij.ide.rpc.dataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.backend.impl.SeBackendItemDataProvidersHolderEntity.Companion.Providers
import com.intellij.platform.searchEverywhere.backend.impl.SeBackendItemDataProvidersHolderEntity.Companion.Session
import com.intellij.platform.searchEverywhere.providers.SeLocalItemDataProvider
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import com.jetbrains.rhizomedb.entities
import com.jetbrains.rhizomedb.exists
import fleet.kernel.DurableRef
import fleet.kernel.change
import fleet.kernel.onDispose
import fleet.kernel.rete.Rete
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class SeBackendService(val project: Project, private val coroutineScope: CoroutineScope) {

  suspend fun getItems(sessionRef: DurableRef<SeSessionEntity>,
                       providerId: SeProviderId,
                       params: SeParams,
                       dataContextId: DataContextId?,
                       requestedCountChannel: ReceiveChannel<Int>
  ): Flow<SeItemData> {
    val provider = getProviders(sessionRef, dataContextId)[providerId] ?: return emptyFlow()

    val requestedCountState = MutableStateFlow(0)
    val receivingJob = coroutineScope.launch {
      requestedCountChannel.consumeEach { count ->
        requestedCountState.update { it + count }
      }
    }

    return flow {
      val itemsFlow = provider.getItems(params)

      itemsFlow.collect { item ->
        requestedCountState.first { it > 0 }
        requestedCountState.update { it - 1 }

        emit(item)
      }
    }.onCompletion {
      receivingJob.cancel()
    }
  }

  private suspend fun getProviders(sessionRef: DurableRef<SeSessionEntity>,
                                   dataContextId: DataContextId?): Map<SeProviderId, SeItemDataProvider> {

    val session = sessionRef.derefOrNull() ?: return emptyMap()
    var existingHolderEntities = entities(Session, session)

    if (existingHolderEntities.isEmpty()) {
      if (dataContextId == null) {
        throw IllegalStateException("Cannot create providers on the backend: no serialized data context")
      }

      val dataContext = withContext(Dispatchers.EDT) {
        dataContextId.dataContext()
      } ?: throw IllegalStateException("Cannot create providers on the backend: couldn't deserialize data context")

      // We may create providers several times, but only one set of providers will be saved as a property to a session entity
      val providers = SeItemsProviderFactory.EP_NAME.extensionList.mapNotNull {
        val provider = it.getItemsProvider(project, dataContext) ?: return@mapNotNull null
        if (provider.id != it.id) {
          SeLog.log { "Backend provider ID mismatch: ${provider.id} != ${it.id}" }
        }
        provider
      }.associate { provider ->
        val id = SeProviderId(provider.id)
        id to SeLocalItemDataProvider(provider, sessionRef, "Backend")
      }

      existingHolderEntities = change {
        if (!session.exists()) {
          providers.values.forEach { Disposer.dispose(it) }
          return@change emptySet()
        }

        val existingEntities = entities(Session, session)
        if (existingEntities.isNotEmpty()) {
          providers.values.forEach { Disposer.dispose(it) }
          existingEntities
        }
        else {
          val entity = SeBackendItemDataProvidersHolderEntity.new {
            it[Providers] = providers
            it[Session] = session
          }

          entity.onDispose(coroutineScope.coroutineContext[Rete]!!) {
            providers.values.forEach { Disposer.dispose(it) }
          }

          setOf(entity)
        }
      }
    }

    return existingHolderEntities.firstOrNull()?.providers ?: emptyMap()
  }

  suspend fun itemSelected(sessionRef: DurableRef<SeSessionEntity>, itemData: SeItemData, modifiers: Int, searchText: String): Boolean {
    val provider = getProviders(sessionRef, null)[itemData.providerId] ?: return false

    return provider.itemSelected(itemData, modifiers, searchText)
  }

  suspend fun getSearchScopesInfoForProvider(
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerId: SeProviderId,
  ): SeSearchScopesInfo? {
    val provider = getProviders(sessionRef, dataContextId)[providerId]
    return provider?.getSearchScopesInfo()
  }

  suspend fun getTypeVisibilityStatesForProvider(
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerId: SeProviderId,
  ): List<SeTypeVisibilityStatePresentation>? {
    val provider = getProviders(sessionRef, dataContextId)[providerId]
    return provider?.getTypeVisibilityStates()
  }

  suspend fun getDisplayNameForProvider(
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
  ): Map<SeProviderId, @Nls String> {
    val allProviders = getProviders(sessionRef, dataContextId)
    return allProviders.filter { providerIds.contains(it.key) }.mapValues { it.value.displayName }
  }

  suspend fun canBeShownInFindResults(
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerId: SeProviderId,
  ): Boolean {
    val provider = getProviders(sessionRef, dataContextId)[providerId] ?: return false
    return provider.canBeShownInFindResults()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SeBackendService = project.service<SeBackendService>()
  }
}