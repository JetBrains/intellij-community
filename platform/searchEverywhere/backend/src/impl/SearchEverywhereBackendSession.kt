// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.platform.kernel.backend.*
import com.intellij.platform.searchEverywhere.*

class SearchEverywhereBackendSession private constructor(
  val providers: Map<SearchEverywhereProviderId, SearchEverywhereItemDataProvider>,
  internal val parentEntity: BackendValueEntity<String>,
) : SearchEverywhereSession {
  override suspend fun saveItem(item: SearchEverywhereItem): SearchEverywhereItemId {
    val entity = newValueEntity(item).apply {
      cascadeDeleteBy(parentEntity)
    }

    return SearchEverywhereItemId(entity.id)
  }

  override suspend fun getItem(itemId: SearchEverywhereItemId): SearchEverywhereItem? {
    return itemId.value.findValueEntity<SearchEverywhereItem>()?.value
  }

  override suspend fun dispose() {
    parentEntity.delete()
  }

  companion object {
    suspend fun create(providers: Map<SearchEverywhereProviderId, SearchEverywhereItemDataProvider>): SearchEverywhereBackendSession {
      val parentEntity = newValueEntity("")
      return SearchEverywhereBackendSession(providers, parentEntity)
    }
  }
}