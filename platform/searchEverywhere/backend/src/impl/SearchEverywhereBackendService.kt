// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.backend.impl.SearchEverywhereBackendItemDataProvidersHolderEntity.Companion.Holder
import com.intellij.platform.searchEverywhere.backend.impl.SearchEverywhereBackendItemDataProvidersHolderEntity.Companion.Session
import com.intellij.platform.searchEverywhere.impl.SearchEverywhereItemEntity
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
class SearchEverywhereBackendService(val project: Project) {

  suspend fun getItems(sessionRef: DurableRef<SearchEverywhereSessionEntity>, providerId: SearchEverywhereProviderId, params: SearchEverywhereParams): Flow<SearchEverywhereItemData> {
    val provider = getProviders(sessionRef)[providerId]

    return provider?.getItems(sessionRef, params) ?: emptyFlow()
  }

  private suspend fun getProviders(sessionRef: DurableRef<SearchEverywhereSessionEntity>): Map<SearchEverywhereProviderId, SearchEverywhereItemDataProvider> =
    withKernel {
      val session = sessionRef.derefOrNull() ?: return@withKernel null
      var existingHolderEntities = entities(Session, session)

      if (existingHolderEntities.isEmpty()) {
        existingHolderEntities = change {
          val holderEntities = entities(Session, session)

          holderEntities.ifEmpty {
            val providers = SearchEverywhereItemsProviderFactory.EP_NAME.extensionList.associate { factory ->
              val provider = factory.getItemsProvider()
              val id = SearchEverywhereProviderId(provider.id)
              id to SearchEverywhereItemDataBackendProvider(id, provider)
            }
            val holder = SearchEverywhereBackendItemDataProvidersHolder(providers)

            SearchEverywhereBackendItemDataProvidersHolderEntity.new {
              it[Holder] = holder
              it[Session] = session
            }

            entities(Session, session)
          }
        }
      }

      existingHolderEntities.first().holder
    }?.providers ?: emptyMap()

  suspend fun itemSelected(itemId: EID) {
    val item = withKernel {
      entity(itemId) as? SearchEverywhereItemEntity
    }?.item

    if (item == null) {
      println("item not found: $itemId")
      return
    }

    println("item selected: ${item}")
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SearchEverywhereBackendService = project.getService(SearchEverywhereBackendService::class.java)
  }
}