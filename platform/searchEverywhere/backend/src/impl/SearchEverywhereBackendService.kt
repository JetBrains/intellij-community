// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.backend.cascadeDeleteBy
import com.intellij.platform.kernel.backend.delete
import com.intellij.platform.kernel.backend.findValueEntity
import com.intellij.platform.kernel.backend.newValueEntity
import com.intellij.platform.searchEverywhere.*
import com.jetbrains.rhizomedb.EID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class SearchEverywhereBackendService(val project: Project) {

  suspend fun startSession(): EID {
    val providers = SearchEverywhereItemsProviderFactory.EP_NAME.extensionList.associate { factory ->
      val provider = factory.getItemsProvider()
      val id = SearchEverywhereProviderId(provider.id)
      id to SearchEverywhereItemDataBackendProvider(id, provider)
    }

    val session = SearchEverywhereBackendSession.create(providers)
    val sessionEntity = newValueEntity(session)
    session.parentEntity.cascadeDeleteBy(sessionEntity)

    return sessionEntity.id
  }

  suspend fun closeSession(sessionId: EID) {
    sessionId.findValueEntity<SearchEverywhereBackendSession>()?.delete()
  }

  suspend fun getProviderIds(sessionId: EID): List<SearchEverywhereProviderId> {
    return sessionId.findValueEntity<SearchEverywhereBackendSession>()?.value?.providers?.keys?.toList() ?: emptyList()
  }

  suspend fun getItems(sessionId: EID, providerId: SearchEverywhereProviderId, params: SearchEverywhereParams): Flow<SearchEverywhereItemData> {
    val session = sessionId.findValueEntity<SearchEverywhereBackendSession>()?.value ?: return emptyFlow()
    return session.providers[providerId]?.getItems(params, session) ?: emptyFlow()
  }

  suspend fun itemSelected(itemId: EID) {
    val item = itemId.findValueEntity<SearchEverywhereItem>()
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