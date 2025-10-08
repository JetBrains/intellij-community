// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.platform.searchEverywhere.*
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
class SeSortedProviderIds(
  val essential: Set<SeProviderId>,
  val adapted: Set<SeProviderId>,
  val nonEssentialNonAdapted: Set<SeProviderId>,
  private val fetchTestItemData: SeItemData?,
) {
  val nonEssential: Set<SeProviderId> get() = nonEssentialNonAdapted + adapted
  val isFetchable: Boolean get() = fetchTestItemData?.fetchItemIfExists() != null

  companion object {
    suspend fun create(providerIds: List<SeProviderId>, providersHolder: SeProvidersHolder, session: SeSession): SeSortedProviderIds {
      val essential: Set<SeProviderId> = providersHolder.getEssentialAllTabProviderIds().filter { it in providerIds }.toSet()
      val adapted: Set<SeProviderId> = providersHolder.adaptedAllTabProviders.filter { it in providerIds }.toSet()
      val nonEssentialNonAdapted: Set<SeProviderId> = providerIds.filter { it !in essential && it !in adapted }.toSet()

      val item = SeFetchTestItem()
      val itemData = SeItemData.createItemData(session, "", item, "".toProviderId(), item.weight(), item.presentation(), emptyMap(), emptyList())

      return SeSortedProviderIds(essential, adapted, nonEssentialNonAdapted, itemData)
    }
  }
}

@ApiStatus.Internal
@Serializable
class SeFetchTestItem : SeItem {
  override val rawObject: Any get() = this

  override fun weight(): Int = 0
  override suspend fun presentation(): SeItemPresentation = SeAdaptedItemEmptyPresentation(false)
}
