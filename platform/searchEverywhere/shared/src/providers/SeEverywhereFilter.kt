// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.platform.searchEverywhere.SeFilter
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeProviderId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeEverywhereFilter(val isAllTab: Boolean, val isEverywhere: Boolean, val disabledProviderIds: List<SeProviderId>) : SeFilter {
  override fun toState(): SeFilterState =
    SeFilterState.Data(mapOf(KEY_ALL_TAB to listOf(isAllTab.toString()),
                             KEY_IS_EVERYWHERE to listOf(isEverywhere.toString()),
                             ENABLED_PROVIDER_IDS to disabledProviderIds.map { it.value }))

  override fun isEqualTo(other: SeFilter): Boolean {
    if (this === other) return true
    if (other !is SeEverywhereFilter) return false

    if (isAllTab != other.isAllTab) return false
    if (isEverywhere != other.isEverywhere) return false
    if (disabledProviderIds != other.disabledProviderIds) return false

    return true
  }

  fun cloneWith(isEverywhere: Boolean): SeEverywhereFilter = SeEverywhereFilter(isAllTab, isEverywhere, disabledProviderIds)
  fun cloneWith(disabledProviderIds: List<SeProviderId>): SeEverywhereFilter = SeEverywhereFilter(isAllTab, isEverywhere, disabledProviderIds)

  companion object {
    const val KEY_ALL_TAB: String = "ALL_TAB"
    const val KEY_IS_EVERYWHERE: String = "IS_EVERYWHERE"
    const val ENABLED_PROVIDER_IDS: String = "ENABLED_PROVIDER_IDS"

    fun from(state: SeFilterState): SeEverywhereFilter {
      when (state) {
        is SeFilterState.Data -> {
          val isAllTab = isAllTab(state) ?: false
          val isEverywhere = isEverywhere(state) ?: false

          val disabledProviderIds = state.get(ENABLED_PROVIDER_IDS)?.map {
            SeProviderId(it)
          } ?: emptyList()

          return SeEverywhereFilter(isAllTab, isEverywhere, disabledProviderIds)
        }
        SeFilterState.Empty -> return SeEverywhereFilter(false, false, emptyList())
      }
    }

    fun isAllTab(state: SeFilterState): Boolean? = state.getBoolean(KEY_ALL_TAB)
    fun isEverywhere(state: SeFilterState): Boolean? = state.getBoolean(KEY_IS_EVERYWHERE)
  }
}