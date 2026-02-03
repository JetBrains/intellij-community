// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

import com.intellij.execution.wsl.sync.WslHashFilter.WslHashExcludeFilter
import com.intellij.execution.wsl.sync.WslHashFilter.WslHashIncludeFilter

/**
 * Must be kept in sync with wslhash.c.
 */
class WslHashFilters private constructor(private val excludes: List<WslHashExcludeFilter> = listOf(),
                                         private val includes: List<WslHashIncludeFilter> = listOf()) {

  /**
   * Checks according to the filter matching specification in wslhash.
   * @see wslhash.c
   */
  fun isFileNameOk(fileName: String): Boolean {
    if (excludes.isEmpty() && includes.isEmpty()) {
      return true
    }
    if (excludes.isEmpty()) {
      return includes.any { it.matches(fileName) }
    }
    if (includes.isEmpty()) {
      return excludes.none { it.matches(fileName) }
    }
    return excludes.none { it.matches(fileName) } || includes.any { it.matches(fileName) }
  }

  /**
   * @return these filters as arguments wslhash.
   * @see wslhash.c
   */
  fun toArgs(): List<String> {
    return excludes.flatMap { it.toArg() } + includes.flatMap { it.toArg() }
  }

  companion object {
    val EMPTY_FILTERS = WslHashFilters()
  }

  class WslHashFiltersBuilder {
    private val excludes: MutableList<WslHashExcludeFilter> = mutableListOf()
    private val includes: MutableList<WslHashIncludeFilter> = mutableListOf()

    fun build(): WslHashFilters {
      if (excludes.isEmpty() && includes.isEmpty()) {
        return EMPTY_FILTERS
      }
      return WslHashFilters(excludes, includes)
    }

    fun exclude(matchers: Iterable<WslHashMatcher>): WslHashFiltersBuilder = apply {
      excludes.addAll(matchers.map { WslHashExcludeFilter(it) })
    }

    fun exclude(vararg matchers: WslHashMatcher): WslHashFiltersBuilder = exclude(matchers.asIterable())

    fun include(matchers: Iterable<WslHashMatcher>): WslHashFiltersBuilder = apply {
      includes.addAll(matchers.map { WslHashIncludeFilter(it) })
    }

    fun include(vararg matchers: WslHashMatcher): WslHashFiltersBuilder = include(matchers.asIterable())

  }

}