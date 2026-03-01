// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.platform.searchEverywhere.SeFilter
import com.intellij.platform.searchEverywhere.SeFilterState
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
    val map = mutableMapOf<String, List<String>>()
    map[TEXT_FILTER] = listOf("true")
    selectedScopeId?.let { map[SELECTED_SCOPE_ID] = listOf(it) }
    selectedType?.let { map[SELECTED_TYPE] = listOf(it) }
    map[MATCH_CASE] = listOf(isCaseSensitive.toString())
    map[WORDS] = listOf(isWholeWordsOnly.toString())
    map[REGEX] = listOf(isRegex.toString())
    return SeFilterState.Data(map)
  }

  override fun isEqualTo(other: SeFilter): Boolean {
    if (this === other) return true
    if (other !is SeTextFilter) return false

    if (selectedScopeId != other.selectedScopeId) return false
    if (selectedType != other.selectedType) return false
    if (isCaseSensitive != other.isCaseSensitive) return false
    if (isWholeWordsOnly != other.isWholeWordsOnly) return false
    if (isRegex != other.isRegex) return false

    return true
  }

  companion object {
    private const val TEXT_FILTER = "TEXT_FILTER"
    private const val SELECTED_SCOPE_ID = "SELECTED_SCOPE_ID"
    private const val SELECTED_TYPE = "SELECTED_TYPE"
    private const val MATCH_CASE = "MATCH_CASE"
    private const val WORDS = "WORDS"
    private const val REGEX = "REGEX"

    fun from(state: SeFilterState): SeTextFilter? {
    if (!isTextFilter(state)) return null
      when (state) {
        is SeFilterState.Data -> {
          val selectedScopeId = state.getOne(SELECTED_SCOPE_ID)
          val selectedType = state.getOne(SELECTED_TYPE)
          val selectedCase = isCaseSensitive(state) ?: false
          val selectedWords = isWholeWordsOnly(state) ?: false
          val selectedRegex = isRegularExpressions(state) ?: false

          return SeTextFilter(selectedScopeId, selectedType, selectedCase, selectedWords, selectedRegex)
        }
        SeFilterState.Empty -> return SeTextFilter(null, null, false, false, false)
      }
    }

    fun isCaseSensitive(state: SeFilterState): Boolean? = state.getBoolean(MATCH_CASE)
    fun isWholeWordsOnly(state: SeFilterState): Boolean? = state.getBoolean(WORDS)
    fun isRegularExpressions(state: SeFilterState): Boolean? = state.getBoolean(REGEX)
    private fun isTextFilter(state: SeFilterState): Boolean = state.getBoolean(TEXT_FILTER) == true
  }
}