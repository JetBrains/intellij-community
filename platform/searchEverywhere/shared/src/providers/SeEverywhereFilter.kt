// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.platform.searchEverywhere.SeFilter
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeProviderId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface SeEverywhereFilter : SeFilter {
  val isAllTab: Boolean
  val isEverywhere: Boolean
  val disabledProviderIds: List<SeProviderId>
}

@ApiStatus.Internal
class SeEverywhereFilterImpl(override val isAllTab: Boolean,
                             override val isEverywhere: Boolean,
                             override val disabledProviderIds: List<SeProviderId>): SeEverywhereFilter {

  override fun toState(): SeFilterState =
    SeFilterState.Data(mapOf(KEY_ALL_TAB to listOf(isAllTab.toString()),
                             KEY_IS_EVERYWHERE to listOf(isEverywhere.toString()),
                             ENABLED_PROVIDER_IDS to disabledProviderIds.map { it.value }))

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

          return SeEverywhereFilterImpl(isAllTab, isEverywhere, disabledProviderIds)
        }
        SeFilterState.Empty -> return SeEverywhereFilterImpl(false, false, emptyList())
      }
    }

    fun isAllTab(state: SeFilterState): Boolean? = state.getBoolean(KEY_ALL_TAB)
    fun isEverywhere(state: SeFilterState): Boolean? = state.getBoolean(KEY_IS_EVERYWHERE)
  }
}

@ApiStatus.Internal
fun SeEverywhereFilter.cloneWith(isEverywhere: Boolean): SeEverywhereFilter = SeEverywhereFilterImpl(isAllTab, isEverywhere, disabledProviderIds)

@ApiStatus.Internal
fun SeEverywhereFilter.cloneWith(disabledProviderIds: List<SeProviderId>): SeEverywhereFilter = SeEverywhereFilterImpl(isAllTab, isEverywhere, disabledProviderIds)
