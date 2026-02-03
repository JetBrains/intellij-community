// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

/**
 * Must be kept in sync with wslhash.c.
 */
abstract class WslHashFilter protected constructor(private val operator: WslHashOperator,
                                                   private val matcher: WslHashMatcher) {
  /**
   * @return true if [fileName] matches, false otherwise.
   */
  fun matches(fileName: String): Boolean {
    return matcher.matches(fileName)
  }

  /**
   * @return this filter as `-f OPERATOR:MATCHER:PATTERN` argument pair for wslhash.
   * @see wslhash.c
   */
  fun toArg(): List<String> {
    return listOf("-f", "${operator.code}:${matcher.code}:${matcher.pattern}")
  }

  class WslHashExcludeFilter(matcher: WslHashMatcher) : WslHashFilter(WslHashOperator.EXCLUDE, matcher)

  class WslHashIncludeFilter(matcher: WslHashMatcher) : WslHashFilter(WslHashOperator.INCLUDE, matcher)
}