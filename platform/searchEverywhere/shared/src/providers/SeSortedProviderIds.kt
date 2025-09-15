// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.platform.searchEverywhere.SeProviderId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
class SeSortedProviderIds(
  val essential: Set<SeProviderId>,
  val adapted: Set<SeProviderId>,
  val nonEssentialNonAdapted: Set<SeProviderId>
) {
  val nonEssential: Set<SeProviderId> get() = nonEssentialNonAdapted + adapted

  companion object {
    fun create(providerIds: List<SeProviderId>, providersHolder: SeProvidersHolder): SeSortedProviderIds {
      val essential: Set<SeProviderId> = providersHolder.getEssentialAllTabProviderIds().filter { it in providerIds }.toSet()
      val adapted: Set<SeProviderId> = providersHolder.adaptedAllTabProviders.filter { it in providerIds }.toSet()
      val nonEssentialNonAdapted: Set<SeProviderId> = providerIds.filter { it !in essential && it !in adapted }.toSet()

      return SeSortedProviderIds(essential, adapted, nonEssentialNonAdapted)
    }
  }
}