// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.backend.impl.SeBackendItemDataProvidersHolderEntity.Companion.Providers
import com.intellij.platform.searchEverywhere.backend.impl.SeBackendItemDataProvidersHolderEntity.Companion.Session
import com.intellij.platform.searchEverywhere.impl.SeItemEntity
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.entities
import com.jetbrains.rhizomedb.entity
import fleet.kernel.DurableRef
import fleet.kernel.change
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class SeBackendService(val project: Project) {

  suspend fun getItems(sessionRef: DurableRef<SeSessionEntity>, providerId: SeProviderId, params: SeParams): Flow<SeItemData> {
    val provider = getProviders(sessionRef)[providerId]

    return provider?.getItems(sessionRef, params) ?: emptyFlow()
  }

  private suspend fun getProviders(sessionRef: DurableRef<SeSessionEntity>): Map<SeProviderId, SeItemDataProvider> =
    withKernel {
      val session = sessionRef.derefOrNull() ?: return@withKernel null
      var existingHolderEntities = entities(Session, session)

      if (existingHolderEntities.isEmpty()) {
        existingHolderEntities = change {
          val holderEntities = entities(Session, session)

          holderEntities.ifEmpty {
            val providers = SeItemsProviderFactory.EP_NAME.extensionList.associate { factory ->
              val provider = factory.getItemsProvider(project)
              val id = SeProviderId(provider.id)
              id to SeItemDataBackendProvider(id, provider)
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

  suspend fun itemSelected(itemId: EID) {
    val item = withKernel {
      entity(itemId) as? SeItemEntity
    }?.item

    if (item == null) {
      println("item not found: $itemId")
      return
    }

    println("item selected: ${item}")
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SeBackendService = project.getService(SeBackendService::class.java)
  }
}