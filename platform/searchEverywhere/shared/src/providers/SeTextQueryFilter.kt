// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.platform.searchEverywhere.SeFilter
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeFilterValue
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTextQueryFilter(val case: Boolean, val words: Boolean, val regex: Boolean) : SeFilter {
  fun cloneWithCase(selectedCase: Boolean): SeTextQueryFilter = SeTextQueryFilter(selectedCase, words, regex)
  fun cloneWithWords(selectedWords: Boolean): SeTextQueryFilter = SeTextQueryFilter(case, selectedWords, regex)
  fun cloneWithRegex(selectedRegex: Boolean): SeTextQueryFilter = SeTextQueryFilter(case, words, selectedRegex)

  override fun toState(): SeFilterState {
    val map = mutableMapOf<String, SeFilterValue>()
    map[MATCH_CASE] = SeFilterValue.One(case.toString())
    map[WORDS] = SeFilterValue.One(words.toString())
    map[REGEX] = SeFilterValue.One(regex.toString())
    return SeFilterState.Data(map)
  }

  companion object {
    private const val MATCH_CASE = "MATCH_CASE"
    private const val WORDS = "WORDS"
    private const val REGEX = "REGEX"

    fun isCaseSensitive(state: SeFilterState): Boolean? =
      (state as? SeFilterState.Data)?.map?.get(MATCH_CASE)?.let { it as? SeFilterValue.One }?.value?.toBoolean()

    fun isWholeWordsOnly(state: SeFilterState): Boolean? =
      (state as? SeFilterState.Data)?.map?.get(WORDS)?.let { it as? SeFilterValue.One }?.value?.toBoolean()

    fun isRegularExpressions(state: SeFilterState): Boolean? =
      (state as? SeFilterState.Data)?.map?.get(REGEX)?.let { it as? SeFilterValue.One }?.value?.toBoolean()
  }
}