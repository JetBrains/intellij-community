// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.platform.kernel.withKernel
import com.intellij.platform.searchEverywhere.impl.SearchEverywhereItemEntity
import com.jetbrains.rhizomedb.entity
import fleet.kernel.DurableRef
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Serializable
data class SearchEverywhereItemData(
  val itemId: SearchEverywhereItemId,
  val providerId: SearchEverywhereProviderId,
  val weight: Int,
  val presentation: SearchEverywhereItemPresentation,
)

@ApiStatus.Experimental
suspend fun SearchEverywhereItemData.Companion.createItemData(
  sessionRef: DurableRef<SearchEverywhereSessionEntity>,
  item: SearchEverywhereItem,
  providerId: SearchEverywhereProviderId,
  weight: Int,
  presentation: SearchEverywhereItemPresentation,
): SearchEverywhereItemData? {
  val entity = SearchEverywhereItemEntity.createWith(sessionRef, item) ?: return null
  return SearchEverywhereItemData(SearchEverywhereItemId(entity.eid), providerId, weight, presentation)
}

suspend fun SearchEverywhereItemData.fetchItemIfExists(): SearchEverywhereItem? {
  return withKernel {
    (entity(itemId.value) as? SearchEverywhereItemEntity)?.item
  }
}
