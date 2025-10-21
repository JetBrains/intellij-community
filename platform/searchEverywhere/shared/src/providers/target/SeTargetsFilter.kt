// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.platform.searchEverywhere.SeFilter
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeFilterValue
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTargetsFilter(val selectedScopeId: String?, val isAutoTogglePossible: Boolean, val hiddenTypes: List<String>?): SeFilter {
  fun cloneWith(selectedScopeId: String?, isAutoTogglePossible: Boolean): SeTargetsFilter = SeTargetsFilter(selectedScopeId, isAutoTogglePossible, hiddenTypes)
  fun cloneWith(hiddenTypes: List<String>?): SeTargetsFilter = SeTargetsFilter(selectedScopeId, isAutoTogglePossible, hiddenTypes)

  override fun toState(): SeFilterState {
    val map = mutableMapOf<String, SeFilterValue>()
    selectedScopeId?.let { map[SELECTED_SCOPE_ID] = SeFilterValue.One(it) }
    map[IS_AUTO_TOGGLE_POSSIBLE] = SeFilterValue.One(isAutoTogglePossible.toString())

    if (hiddenTypes?.isNotEmpty() == true) {
      map[HIDDEN_TYPES] = SeFilterValue.Many(hiddenTypes)
    }
    return SeFilterState.Data(map)
  }

  companion object {
    private const val SELECTED_SCOPE_ID = "SELECTED_SCOPE_ID"
    private const val IS_AUTO_TOGGLE_POSSIBLE: String = "IS_AUTO_TOGGLE_POSSIBLE"
    private const val HIDDEN_TYPES = "HIDDEN_TYPES"

    fun from(state: SeFilterState): SeTargetsFilter {
      when (state) {
        is SeFilterState.Data -> {
          val map = state.map

          val selectedScopeId = map[SELECTED_SCOPE_ID]?.let {
            it as? SeFilterValue.One
          }?.value

          val isAutoTogglePossible = state.map[IS_AUTO_TOGGLE_POSSIBLE]?.let { it as? SeFilterValue.One }?.value?.toBoolean() ?: false

          val hiddenTypes = map[HIDDEN_TYPES]?.let {
            it as? SeFilterValue.Many
          }?.values ?: emptyList()

          return SeTargetsFilter(selectedScopeId, isAutoTogglePossible, hiddenTypes)
        }
        SeFilterState.Empty -> return SeTargetsFilter(null, false, emptyList())
      }
    }
  }
}