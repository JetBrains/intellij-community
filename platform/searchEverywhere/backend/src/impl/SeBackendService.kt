// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.api.SeItemDataProvider
import com.intellij.platform.searchEverywhere.api.SeItemsProviderFactory
import com.intellij.platform.searchEverywhere.backend.impl.SeBackendItemDataProvidersHolderEntity.Companion.Providers
import com.intellij.platform.searchEverywhere.backend.impl.SeBackendItemDataProvidersHolderEntity.Companion.Session
import com.jetbrains.rd.ide.model.ActionTimestampSetModel
import com.jetbrains.rdserver.actions.createDataContext
import com.jetbrains.rhizomedb.entities
import fleet.kernel.DurableRef
import fleet.kernel.change
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class SeBackendService(val project: Project) {

  suspend fun getItems(sessionRef: DurableRef<SeSessionEntity>,
                       providerId: SeProviderId,
                       params: SeParams,
                       timestampSetModel: ActionTimestampSetModel
  ): Flow<SeItemData> {
    val provider = getProviders(sessionRef, timestampSetModel)[providerId]

    return provider?.getItems(params) ?: emptyFlow()
  }

  private suspend fun getProviders(sessionRef: DurableRef<SeSessionEntity>,
                                   timestampSetModel: ActionTimestampSetModel?): Map<SeProviderId, SeItemDataProvider> =
    withKernel {
      val session = sessionRef.derefOrNull() ?: return@withKernel null
      var existingHolderEntities = entities(Session, session)

      if (existingHolderEntities.isEmpty()) {
        if (timestampSetModel == null) {
          throw IllegalStateException("Cannot create providers on the backend: no timestamp model")
        }

        existingHolderEntities = change {
          val holderEntities = entities(Session, session)
          val clientAppSession = ClientSessionsManager.getAppSession(session.clientId) ?: throw IllegalStateException("No client session")

          holderEntities.ifEmpty {
            val dataContext = createDataContext(clientAppSession, session.actionId, timestampSetModel)

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

      existingHolderEntities.first().providers
    } ?: emptyMap()

  suspend fun itemSelected(sessionRef: DurableRef<SeSessionEntity>, itemData: SeItemData, modifiers: Int, searchText: String): Boolean {
    val provider = getProviders(sessionRef, null)[itemData.providerId] ?: return false

    return provider.itemSelected(itemData, modifiers, searchText)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SeBackendService = project.service<SeBackendService>()
  }
}