// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.platform.searchEverywhere.SeFilter
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeFilterValue
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTextFilter(val selectedScopeId: String?, val selectedType: String?): SeFilter {
  fun cloneWithScope(selectedScopeId: String?): SeTextFilter = SeTextFilter(selectedScopeId, selectedType)
  fun cloneWithType(selectedType: String?): SeTextFilter = SeTextFilter(selectedScopeId, selectedType)

  override fun toState(): SeFilterState {
    val map = mutableMapOf<String, SeFilterValue>()
    selectedScopeId?.let { map[SELECTED_SCOPE_ID] = SeFilterValue.One(it) }
    selectedType?.let { map[SELECTED_TYPE] = SeFilterValue.One(it) }
    return SeFilterState.Data(map)
  }

  companion object {
    private const val SELECTED_SCOPE_ID = "SELECTED_SCOPE_ID"
    private const val SELECTED_TYPE = "SELECTED_TYPE"

    fun from(state: SeFilterState): SeTextFilter {
      when (state) {
        is SeFilterState.Data -> {
          val map = state.map

          val selectedScopeId = map[SELECTED_SCOPE_ID]?.let {
            it as? SeFilterValue.One
          }?.value

          val selectedType = map[SELECTED_TYPE]?.let {
            it as? SeFilterValue.One
          }?.value

          return SeTextFilter(selectedScopeId, selectedType)
        }
        SeFilterState.Empty -> return SeTextFilter(null, null)
      }
    }
  }
}