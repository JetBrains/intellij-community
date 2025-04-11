// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.platform.searchEverywhere.SeFilter
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeFilterValue
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeEverywhereFilter(val isEverywhere: Boolean): SeFilter {
  override fun toState(): SeFilterState =
    SeFilterState.Data(mapOf(KEY_IS_EVERYWHERE to SeFilterValue.One(isEverywhere.toString())))

  companion object {
    const val KEY_IS_EVERYWHERE: String = "IS_EVERYWHERE"

    fun from(state: SeFilterState): SeEverywhereFilter {
      when (state) {
        is SeFilterState.Data -> {
          val isEverywhere = state.map[KEY_IS_EVERYWHERE]?.let {
            it as? SeFilterValue.One
          }?.value?.toBoolean() ?: false

          return SeEverywhereFilter(isEverywhere)
        }
        SeFilterState.Empty -> return SeEverywhereFilter(false)
      }
    }
  }
}