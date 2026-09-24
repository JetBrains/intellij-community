// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.platform.searchEverywhere.SeFilter
import com.intellij.platform.searchEverywhere.SeFilterState
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTargetsFilter(
  val selectedScopeId: String?,
  val isAutoTogglePossible: Boolean,
  val hiddenTypes: Map<String, List<String>>,
) : SeFilter {
  fun cloneWith(selectedScopeId: String?, isAutoTogglePossible: Boolean): SeTargetsFilter =
    SeTargetsFilter(selectedScopeId, isAutoTogglePossible, hiddenTypes)

  fun cloneWith(key: String, hidden: List<String>): SeTargetsFilter =
    SeTargetsFilter(selectedScopeId, isAutoTogglePossible, hiddenTypes + (key to hidden))

  override fun toState(): SeFilterState {
    val map = mutableMapOf<String, List<String>>()
    selectedScopeId?.let { map[SELECTED_SCOPE_ID] = listOf(it) }
    map[IS_AUTO_TOGGLE_POSSIBLE] = listOf(isAutoTogglePossible.toString())
    hiddenTypes.forEach { (key, hidden) -> map[hiddenTypesKey(key)] = hidden }

    return SeFilterState.Data(map)
  }

  companion object {
    private const val SELECTED_SCOPE_ID = "SELECTED_SCOPE_ID"
    private const val IS_AUTO_TOGGLE_POSSIBLE: String = "IS_AUTO_TOGGLE_POSSIBLE"
    private const val HIDDEN_TYPES_PREFIX = "HIDDEN_TYPES."

    private fun hiddenTypesKey(key: String): String = "$HIDDEN_TYPES_PREFIX$key"

    fun from(state: SeFilterState): SeTargetsFilter {
      when (state) {
        is SeFilterState.Data -> {
          val selectedScopeId = state.getOne(SELECTED_SCOPE_ID)
          val isAutoTogglePossible = state.getBoolean(IS_AUTO_TOGGLE_POSSIBLE) ?: false
          val hiddenTypes = state.keys
            .filter { it.startsWith(HIDDEN_TYPES_PREFIX) }
            .associate { it.removePrefix(HIDDEN_TYPES_PREFIX) to state.get(it).orEmpty() }

          return SeTargetsFilter(selectedScopeId, isAutoTogglePossible, hiddenTypes)
        }
        SeFilterState.Empty -> return SeTargetsFilter(null, false, emptyMap())
      }
    }
  }
}
