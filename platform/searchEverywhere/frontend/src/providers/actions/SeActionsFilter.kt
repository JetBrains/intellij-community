// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.platform.searchEverywhere.SeFilter
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilter
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeActionsFilter(val includeDisabled: Boolean): SeFilter {
  override fun toState(): SeFilterState =
    SeFilterState.Data(mapOf(KEY_INCLUDE_DISABLED to includeDisabled.toString()))

  companion object {
    private const val KEY_INCLUDE_DISABLED = "INCLUDE_DISABLED"

    fun from(state: SeFilterState): SeActionsFilter {
      when (state) {
        is SeFilterState.Data -> {
          return SeActionsFilter((state.map[KEY_INCLUDE_DISABLED] ?: state.map[SeEverywhereFilter.KEY_IS_EVERYWHERE]) == "true")
        }
        SeFilterState.Empty -> return SeActionsFilter(false)
      }
    }
  }
}