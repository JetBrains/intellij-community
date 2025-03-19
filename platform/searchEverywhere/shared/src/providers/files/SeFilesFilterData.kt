// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.files

import com.intellij.platform.searchEverywhere.SeFilterState
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeFilesFilterData(val isProjectOnly: Boolean) {
  fun toFilterData(): SeFilterState = SeFilterState.Data(mapOf(KEY_IS_PROJECT_ONLY to isProjectOnly.toString()))

  companion object {
    private const val KEY_IS_PROJECT_ONLY = "IS_PROJECT_ONLY"

    fun from(state: SeFilterState): SeFilesFilterData {
      when (state) {
        is SeFilterState.Data -> {
          val map = state.map.toMutableMap() ?: emptyMap()
          return SeFilesFilterData(map[KEY_IS_PROJECT_ONLY] == "true")
        }
        SeFilterState.Empty -> return SeFilesFilterData(false)
      }
    }
  }
}