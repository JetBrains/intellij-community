// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.ide.rpc.deserializeFromRpc
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.api.SeItemDataProvider
import com.intellij.platform.searchEverywhere.api.SeItemsProviderFactory
import com.intellij.platform.searchEverywhere.backend.impl.SeBackendItemDataProvidersHolderEntity.Companion.Providers
import com.intellij.platform.searchEverywhere.backend.impl.SeBackendItemDataProvidersHolderEntity.Companion.Session
import com.jetbrains.rhizomedb.entities
import fleet.kernel.DurableRef
import fleet.kernel.change
import fleet.util.openmap.SerializedValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class SeBackendService(val project: Project) {

  suspend fun getItems(sessionRef: DurableRef<SeSessionEntity>,
                       providerId: SeProviderId,
                       params: SeParams,
                       serializedDataContext: SerializedValue?
  ): Flow<SeItemData> {
    val provider = getProviders(sessionRef, serializedDataContext)[providerId]

    return provider?.getItems(params) ?: emptyFlow()
  }

  private suspend fun getProviders(sessionRef: DurableRef<SeSessionEntity>,
                                   serializedDataContext: SerializedValue?): Map<SeProviderId, SeItemDataProvider> {

    val session = sessionRef.derefOrNull() ?: return emptyMap()
    var existingHolderEntities = entities(Session, session)
    val clientId = ClientId.current

    if (existingHolderEntities.isEmpty()) {
      if (serializedDataContext == null) {
        throw IllegalStateException("Cannot create providers on the backend: no serialized data context")
      }

      val dataContext = withContext(clientId.asContextElement() +
                                    Dispatchers.EDT +
                                    ModalityState.any().asContextElement()) {
        deserializeFromRpc(serializedDataContext, DataContext::class)
      }

      existingHolderEntities = change {
        val holderEntities = entities(Session, session)

        holderEntities.ifEmpty {
          if (dataContext == null) {
            throw IllegalStateException("Cannot create providers on the backend: couldn't deserialize data context")
          }

          val providers = SeItemsProviderFactory.EP_NAME.extensionList.associate { factory ->
            val provider = factory.getItemsProvider(project, dataContext)
            val id = SeProviderId(provider.id)
            id to SeItemDataBackendProvider(id, provider, sessionRef)
          }

          SeBackendItemDataProvidersHolderEntity.new {
            it[Providers] = providers
            it[Session] = session
          }

          entities(Session, session)
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