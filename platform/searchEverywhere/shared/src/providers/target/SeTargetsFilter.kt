// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.platform.searchEverywhere.SeFilter
import com.intellij.platform.searchEverywhere.SeFilterState
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTargetsFilter(val selectedScopeId: String?, val isAutoTogglePossible: Boolean, val hiddenTypes: List<String>?): SeFilter {
  fun cloneWith(selectedScopeId: String?, isAutoTogglePossible: Boolean): SeTargetsFilter = SeTargetsFilter(selectedScopeId, isAutoTogglePossible, hiddenTypes)
  fun cloneWith(hiddenTypes: List<String>?): SeTargetsFilter = SeTargetsFilter(selectedScopeId, isAutoTogglePossible, hiddenTypes)

  override fun toState(): SeFilterState {
    val map = mutableMapOf<String, List<String>>()
    selectedScopeId?.let { map[SELECTED_SCOPE_ID] = listOf(it) }
    map[IS_AUTO_TOGGLE_POSSIBLE] = listOf(isAutoTogglePossible.toString())

    if (hiddenTypes?.isNotEmpty() == true) {
      map[HIDDEN_TYPES] = hiddenTypes
    }
    return SeFilterState.Data(map)
  }

  override fun isEqualTo(other: SeFilter): Boolean {
    if (this === other) return true
    if (other !is SeTargetsFilter) return false

    if (selectedScopeId != other.selectedScopeId) return false
    if (isAutoTogglePossible != other.isAutoTogglePossible) return false
    if (hiddenTypes != other.hiddenTypes) return false

    return true
  }

  companion object {
    private const val SELECTED_SCOPE_ID = "SELECTED_SCOPE_ID"
    private const val IS_AUTO_TOGGLE_POSSIBLE: String = "IS_AUTO_TOGGLE_POSSIBLE"
    private const val HIDDEN_TYPES = "HIDDEN_TYPES"

    fun from(state: SeFilterState): SeTargetsFilter {
      when (state) {
        is SeFilterState.Data -> {
          val selectedScopeId = state.getOne(SELECTED_SCOPE_ID)
          val isAutoTogglePossible = state.getBoolean(IS_AUTO_TOGGLE_POSSIBLE) ?: false
          val hiddenTypes = state.get(HIDDEN_TYPES) ?: emptyList()

          return SeTargetsFilter(selectedScopeId, isAutoTogglePossible, hiddenTypes)
        }
        SeFilterState.Empty -> return SeTargetsFilter(null, false, emptyList())
      }
    }
  }
}