// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeItemDataFactory
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.presentations.SeAdaptedItemEmptyPresentation
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.toProviderId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
class SeSortedProviderIds(
  val essential: Set<SeProviderId>,
  val adapted: SeSortedAdaptedProviderIds,
  val adaptedWithPresentation: SeSortedAdaptedProviderIds,
  val nonEssentialNonAdapted: Set<SeProviderId>,
  private val fetchTestItemData: SeItemData?,
  private val originalBackendLegacyContributorsRef: SeLegacyContributorsRef?
) {
  val nonEssential: Set<SeProviderId> get() = nonEssentialNonAdapted + adapted.allTab + adaptedWithPresentation.allTab
  val isFetchable: Boolean get() = fetchTestItemData?.fetchItemIfExists() != null
  val originalBackendLegacyContributors: SeLegacyContributors? get() = originalBackendLegacyContributorsRef?.findContributorsOrNull()

  fun adaptedWithPresentationOrFetchable(localLegacyContributors: Set<SeProviderId>): SeSortedAdaptedProviderIds = SeSortedAdaptedProviderIds(
    ((adapted.allTab.takeIf { isFetchable }?.filter { localLegacyContributors.contains(it) } ?: emptySet()) + adaptedWithPresentation.allTab).toSet(),
    (adapted.separateTab.takeIf { isFetchable }?.filter { localLegacyContributors.contains(it.providerId) } ?: emptySet()) + adaptedWithPresentation.separateTab
  )

  companion object {
    suspend fun create(providerIds: List<SeProviderId>, providersHolder: SeProvidersHolder, session: SeSession): SeSortedProviderIds {
      val essential: Set<SeProviderId> = providersHolder.getEssentialAllTabProviderIds().filter { it in providerIds }.toSet()

      val adapted = SeSortedAdaptedProviderIds(providersHolder.adaptedAllTabProviders(false).filter { it in providerIds }.toSet(),
                                               providersHolder.adaptedTabInfos(false).filter { it.providerId in providerIds })
      val adaptedWithPresentation = SeSortedAdaptedProviderIds(providersHolder.adaptedAllTabProviders(true).filter { it in providerIds }.toSet(),
                                                               providersHolder.adaptedTabInfos(true).filter { it.providerId in providerIds })

      val nonEssentialNonAdapted: Set<SeProviderId> = providerIds.filter { it !in essential && it !in adapted.allTab && it !in adaptedWithPresentation.allTab }.toSet()

      val item = SeFetchTestItem()
      val itemData = SeItemDataFactory().createItemData(session, "", item, "".toProviderId(), emptyMap())

      val legacyContributorsRef = SeLegacyContributorsRefImpl.create(session, providersHolder.legacyContributors)

      return SeSortedProviderIds(essential, adapted, adaptedWithPresentation, nonEssentialNonAdapted, itemData, legacyContributorsRef)
    }
  }
}

@ApiStatus.Internal
@Serializable
class SeSortedAdaptedProviderIds(val allTab: Set<SeProviderId>,
                                 val separateTab: List<SeLegacyTabInfo>)

@ApiStatus.Internal
@Serializable
class SeFetchTestItem : SeItem {
  override val rawObject: Any get() = this

  override fun weight(): Int = 0
  override suspend fun presentation(): SeItemPresentation = SeAdaptedItemEmptyPresentation(false)
}
