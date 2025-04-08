// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.files

import com.intellij.platform.searchEverywhere.SeFilter
import com.intellij.platform.searchEverywhere.SeFilterState
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeTargetsFilter(val selectedScopeId: String?): SeFilter {
  override fun toState(): SeFilterState = selectedScopeId?.let {
    SeFilterState.Data(mapOf(SELECTED_SCOPE_ID to it))
  } ?: SeFilterState.Empty

  companion object {
    private const val SELECTED_SCOPE_ID = "SELECTED_SCOPE_ID"

    fun from(state: SeFilterState): SeTargetsFilter {
      when (state) {
        is SeFilterState.Data -> {
          val map = state.map.toMutableMap()
          return SeTargetsFilter(map[SELECTED_SCOPE_ID])
        }
        SeFilterState.Empty -> return SeTargetsFilter(null)
      }
    }
  }
}