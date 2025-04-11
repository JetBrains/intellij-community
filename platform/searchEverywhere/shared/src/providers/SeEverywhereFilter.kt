// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.platform.searchEverywhere.SeFilter
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeFilterValue
import com.intellij.platform.searchEverywhere.SeProviderId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeEverywhereFilter(val isEverywhere: Boolean, val disabledProviderIds: List<SeProviderId>): SeFilter {
  override fun toState(): SeFilterState =
    SeFilterState.Data(mapOf(KEY_IS_EVERYWHERE to SeFilterValue.One(isEverywhere.toString()),
                             ENABLED_PROVIDER_IDS to SeFilterValue.Many(disabledProviderIds.map { it.value })))

  fun cloneWith(isEverywhere: Boolean): SeEverywhereFilter = SeEverywhereFilter(isEverywhere, disabledProviderIds)
  fun cloneWith(disabledProviderIds: List<SeProviderId>): SeEverywhereFilter = SeEverywhereFilter(isEverywhere, disabledProviderIds)

  companion object {
    const val KEY_IS_EVERYWHERE: String = "IS_EVERYWHERE"
    const val ENABLED_PROVIDER_IDS: String = "ENABLED_PROVIDER_IDS"

    fun from(state: SeFilterState): SeEverywhereFilter {
      when (state) {
        is SeFilterState.Data -> {
          val isEverywhere = state.map[KEY_IS_EVERYWHERE]?.let {
            it as? SeFilterValue.One
          }?.value?.toBoolean() ?: false

          val disabledProviderIds = state.map[ENABLED_PROVIDER_IDS]?.let {
            it as? SeFilterValue.Many
          }?.values?.map {
            SeProviderId(it)
          } ?: emptyList()

          return SeEverywhereFilter(isEverywhere, disabledProviderIds)
        }
        SeFilterState.Empty -> return SeEverywhereFilter(false, emptyList())
      }
    }
  }
}