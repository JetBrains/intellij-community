// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.platform.searchEverywhere.SeFilter
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeFilterValue
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTextFilter(
  val selectedScopeId: String?,
  val selectedType: String?,
  val isCaseSensitive: Boolean,
  val isWholeWordsOnly: Boolean,
  val isRegex: Boolean,
) : SeFilter {
  fun cloneWithScope(selectedScopeId: String?): SeTextFilter = SeTextFilter(selectedScopeId, selectedType, isCaseSensitive, isWholeWordsOnly, isRegex)
  fun cloneWithType(selectedType: String?): SeTextFilter = SeTextFilter(selectedScopeId, selectedType, isCaseSensitive, isWholeWordsOnly, isRegex)
  fun cloneWithCase(selectedCase: Boolean): SeTextFilter = SeTextFilter(selectedScopeId, selectedType, selectedCase, isWholeWordsOnly, isRegex)
  fun cloneWithWords(selectedWords: Boolean): SeTextFilter = SeTextFilter(selectedScopeId, selectedType, isCaseSensitive, selectedWords, isRegex)
  fun cloneWithRegex(selectedRegex: Boolean): SeTextFilter = SeTextFilter(selectedScopeId, selectedType, isCaseSensitive, isWholeWordsOnly, selectedRegex)

  override fun toState(): SeFilterState {
    val map = mutableMapOf<String, SeFilterValue>()
    selectedScopeId?.let { map[SELECTED_SCOPE_ID] = SeFilterValue.One(it) }
    selectedType?.let { map[SELECTED_TYPE] = SeFilterValue.One(it) }
    map[MATCH_CASE] = SeFilterValue.One(isCaseSensitive.toString())
    map[WORDS] = SeFilterValue.One(isWholeWordsOnly.toString())
    map[REGEX] = SeFilterValue.One(isRegex.toString())
    return SeFilterState.Data(map)
  }

  companion object {
    private const val SELECTED_SCOPE_ID = "SELECTED_SCOPE_ID"
    private const val SELECTED_TYPE = "SELECTED_TYPE"
    private const val MATCH_CASE = "MATCH_CASE"
    private const val WORDS = "WORDS"
    private const val REGEX = "REGEX"

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
          val selectedCase = isCaseSensitive(state) ?: false
          val selectedWords = isWholeWordsOnly(state) ?: false
          val selectedRegex = isRegularExpressions(state) ?: false

          return SeTextFilter(selectedScopeId, selectedType, selectedCase, selectedWords, selectedRegex)
        }
        SeFilterState.Empty -> return SeTextFilter(null, null, false, false, false)
      }
    }

    fun isCaseSensitive(state: SeFilterState): Boolean? =
      (state as? SeFilterState.Data)?.map?.get(MATCH_CASE)?.let { it as? SeFilterValue.One }?.value?.toBoolean()

    fun isWholeWordsOnly(state: SeFilterState): Boolean? =
      (state as? SeFilterState.Data)?.map?.get(WORDS)?.let { it as? SeFilterValue.One }?.value?.toBoolean()

    fun isRegularExpressions(state: SeFilterState): Boolean? =
      (state as? SeFilterState.Data)?.map?.get(REGEX)?.let { it as? SeFilterValue.One }?.value?.toBoolean()
  }
}