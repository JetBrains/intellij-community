// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.platform.kernel.withKernel
import com.intellij.platform.searchEverywhere.api.SeItem
import com.intellij.platform.searchEverywhere.impl.SeItemEntity
import com.jetbrains.rhizomedb.entity
import fleet.kernel.DurableRef
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Serializable
data class SeItemData(
  val itemId: SeItemId,
  val providerId: SeProviderId,
  val weight: Int,
  val presentation: SeItemPresentation,
)

@ApiStatus.Experimental
suspend fun SeItemData.Companion.createItemData(
  sessionRef: DurableRef<SeSessionEntity>,
  item: SeItem,
  providerId: SeProviderId,
  weight: Int,
  presentation: SeItemPresentation,
): SeItemData? {
  val entity = SeItemEntity.createWith(sessionRef, item) ?: return null
  return SeItemData(SeItemId(entity.eid), providerId, weight, presentation)
}

suspend fun SeItemData.fetchItemIfExists(): SeItem? {
  return withKernel {
    (entity(itemId.value) as? SeItemEntity)?.item
  }
}
