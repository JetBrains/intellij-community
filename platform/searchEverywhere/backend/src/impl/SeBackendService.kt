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
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.backend.impl.SeBackendItemDataProvidersHolderEntity.Companion.ProvidersHolder
import com.intellij.platform.searchEverywhere.backend.impl.SeBackendItemDataProvidersHolderEntity.Companion.Session
import com.intellij.platform.searchEverywhere.providers.SeProvidersHolder
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
    val provider = getProvidersHolder(sessionRef, dataContextId)?.get(providerId, false) ?: return emptyFlow()

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

  private suspend fun getProvidersHolder(sessionRef: DurableRef<SeSessionEntity>,
                                         dataContextId: DataContextId?): SeProvidersHolder? {

    val session = sessionRef.derefOrNull() ?: return null
    var existingHolderEntities = entities(Session, session)

    if (existingHolderEntities.isEmpty()) {
      if (dataContextId == null) {
        throw IllegalStateException("Cannot create providers on the backend: no serialized data context")
      }

      val dataContext = withContext(Dispatchers.EDT) {
        dataContextId.dataContext()
      } ?: throw IllegalStateException("Cannot create providers on the backend: couldn't deserialize data context")

      val actionEvent = AnActionEvent.createEvent(dataContext, null, "", ActionUiKind.NONE, null)

      // We may create providers several times, but only one set of providers will be saved as a property to a session entity
      val providersHolder = SeProvidersHolder.initialize(actionEvent, project, sessionRef, "Backend")

      existingHolderEntities = change {
        if (!session.exists()) {
          Disposer.dispose(providersHolder)
          return@change emptySet()
        }

        val existingEntities = entities(Session, session)
        if (existingEntities.isNotEmpty()) {
          Disposer.dispose(providersHolder)
          existingEntities
        }
        else {
          val entity = SeBackendItemDataProvidersHolderEntity.new {
            it[ProvidersHolder] = providersHolder
            it[Session] = session
          }

          entity.onDispose(coroutineScope.coroutineContext[Rete]!!) {
            Disposer.dispose(providersHolder)
          }

          setOf(entity)
        }
      }
    }

    return existingHolderEntities.firstOrNull()?.providersHolder
  }

  suspend fun itemSelected(sessionRef: DurableRef<SeSessionEntity>, itemData: SeItemData, modifiers: Int, searchText: String): Boolean {
    val provider = getProvidersHolder(sessionRef, null)?.get(itemData.providerId, false) ?: return false

    return provider.itemSelected(itemData, modifiers, searchText)
  }

  suspend fun getSearchScopesInfoForProvider(
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerId: SeProviderId,
  ): SeSearchScopesInfo? {
    val provider = getProvidersHolder(sessionRef, dataContextId)?.get(providerId, false)
    return provider?.getSearchScopesInfo()
  }

  suspend fun getTypeVisibilityStatesForProvider(
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerId: SeProviderId,
  ): List<SeTypeVisibilityStatePresentation>? {
    val provider = getProvidersHolder(sessionRef, dataContextId)?.get(providerId, false)
    return provider?.getTypeVisibilityStates()
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
    providerId: SeProviderId,
  ): Boolean {
    val provider = getProvidersHolder(sessionRef, dataContextId)?.get(providerId, false) ?: return false
    return provider.canBeShownInFindResults()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SeBackendService = project.service<SeBackendService>()
  }
}