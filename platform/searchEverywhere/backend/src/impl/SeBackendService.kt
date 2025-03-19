// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.ide.rpc.DataContextId
import com.intellij.ide.rpc.dataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.backend.impl.SeBackendItemDataProvidersHolderEntity.Companion.Providers
import com.intellij.platform.searchEverywhere.backend.impl.SeBackendItemDataProvidersHolderEntity.Companion.Session
import com.jetbrains.rhizomedb.entities
import com.jetbrains.rhizomedb.exists
import fleet.kernel.DurableRef
import fleet.kernel.change
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class SeBackendService(val project: Project) {

  suspend fun getItems(sessionRef: DurableRef<SeSessionEntity>,
                       providerId: SeProviderId,
                       params: SeParams,
                       dataContextId: DataContextId?
  ): Flow<SeItemData> {
    val provider = getProviders(sessionRef, dataContextId)[providerId]

    return provider?.getItems(params) ?: emptyFlow()
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
      val providers = SeItemsProviderFactory.EP_NAME.extensionList.asFlow().map {
        it.getItemsProvider(project, dataContext)
      }.toList().associate { provider ->
        val id = SeProviderId(provider.id)
        id to SeItemDataBackendProvider(id, provider, sessionRef)
      }

      existingHolderEntities = change {
        if (!session.exists()) return@change emptySet()

        entities(Session, session).ifEmpty {
          val entity = SeBackendItemDataProvidersHolderEntity.new {
            it[Providers] = providers
            it[Session] = session
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

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SeBackendService = project.service<SeBackendService>()
  }
}