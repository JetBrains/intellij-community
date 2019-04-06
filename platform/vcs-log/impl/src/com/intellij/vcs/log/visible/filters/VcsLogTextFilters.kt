// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible.filters

import com.intellij.vcs.log.VcsLogDetailsFilter
import com.intellij.vcs.log.VcsLogTextFilter
import java.util.regex.Pattern

class VcsLogRegexTextFilter internal constructor(private val pattern: Pattern) : VcsLogDetailsFilter, VcsLogTextFilter {
  override fun matches(message: String): Boolean = pattern.matcher(message).find()

  override fun getText(): String = pattern.pattern()

  override fun isRegex(): Boolean = true

  override fun matchesCase(): Boolean = (pattern.flags() and Pattern.CASE_INSENSITIVE) == 0

  override fun toString(): String {
    return "matching '$text' ${caseSensitiveText()}"
  }
}

class VcsLogMultiplePatternsTextFilter internal constructor(val patterns: List<String>,
                                                            private val isMatchCase: Boolean) : VcsLogDetailsFilter, VcsLogTextFilter {
  override fun getText(): String = if (patterns.size == 1) patterns.single() else patterns.joinToString("|") { Pattern.quote(it) }

  override fun isRegex(): Boolean = patterns.size > 1

  override fun matchesCase(): Boolean = isMatchCase

  override fun matches(message: String): Boolean = patterns.any { message.contains(it, !isMatchCase) }

  override fun toString(): String {
    return "containing at least one of the ${patterns.joinToString(", ") { s -> "'$s'" }} ${caseSensitiveText()}"
  }

}

internal fun VcsLogTextFilter.caseSensitiveText() = "(case ${if (matchesCase()) "sensitive" else "insensitive"})"