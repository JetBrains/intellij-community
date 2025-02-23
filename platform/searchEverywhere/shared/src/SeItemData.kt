// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.platform.kernel.withKernel
import com.intellij.platform.searchEverywhere.api.SeItem
import com.intellij.platform.searchEverywhere.impl.SeItemEntity
import fleet.kernel.DurableRef
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Experimental
@ApiStatus.Internal
data class SeItemData(
  val itemId: SeItemId,
  val providerId: SeProviderId,
  val weight: Int,
  val presentation: SeItemPresentation,
  private val itemRef: DurableRef<SeItemEntity>
) {

  suspend fun fetchItemIfExists(): SeItem? {
    return withKernel {
      itemRef.derefOrNull()?.findItemOrNull()
    }
  }

  @ApiStatus.Internal
  companion object {
    suspend fun createItemData(
      sessionRef: DurableRef<SeSessionEntity>,
      item: SeItem,
      providerId: SeProviderId,
      weight: Int,
      presentation: SeItemPresentation,
    ): SeItemData? {
      val entityRef = SeItemEntity.createWith(sessionRef, item) ?: return null

      // TODO: Seems like we don't need this id. We should rid of this as soon as SeResultsAccumulator stops using it.
      val itemId = withKernel { entityRef.derefOrNull()?.eid } ?: return null

      return SeItemData(SeItemId(itemId), providerId, weight, presentation, entityRef)
    }
  }
}
