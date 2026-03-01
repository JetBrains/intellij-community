// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.platform.searchEverywhere.SeFilter
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilter
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeActionsFilter(val includeDisabled: Boolean, val isAutoTogglePossible: Boolean) : SeFilter {
  override fun toState(): SeFilterState =
    SeFilterState.Data(mapOf(
      KEY_INCLUDE_DISABLED to listOf(includeDisabled.toString()),
      IS_AUTO_TOGGLE_POSSIBLE to listOf(isAutoTogglePossible.toString())
    ))

  override fun isEqualTo(other: SeFilter): Boolean {
    if (this === other) return true
    if (other !is SeActionsFilter) return false

    if (includeDisabled != other.includeDisabled) return false
    if (isAutoTogglePossible != other.isAutoTogglePossible) return false

    return true
  }

  companion object {
    private const val KEY_INCLUDE_DISABLED = "INCLUDE_DISABLED"
    private const val IS_AUTO_TOGGLE_POSSIBLE: String = "IS_AUTO_TOGGLE_POSSIBLE"

    fun from(state: SeFilterState): SeActionsFilter {
      when (state) {
        is SeFilterState.Data -> {
          val includeDisabled = state.getBoolean(KEY_INCLUDE_DISABLED)
                                ?: SeEverywhereFilter.isEverywhere(state)
                                ?: false

          val isAutoTogglePossible = state.getBoolean(IS_AUTO_TOGGLE_POSSIBLE) ?: false

          return SeActionsFilter(includeDisabled, isAutoTogglePossible)
        }
        SeFilterState.Empty -> return SeActionsFilter(false, isAutoTogglePossible = false)
      }
    }
  }
}