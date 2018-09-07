/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.ui.filter

import com.intellij.vcs.log.VcsLogDetailsFilter
import com.intellij.vcs.log.VcsLogTextFilter
import com.intellij.vcs.log.util.VcsLogUtil
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class VcsLogTextFilterImpl internal constructor(private val text: String,
                                                private val isMatchCase: Boolean) : VcsLogDetailsFilter, VcsLogTextFilter {
  @Suppress("unused")
  // used in upsource
  constructor(text: String) : this(text, false)

  override fun matches(message: String): Boolean = message.contains(text, !isMatchCase)

  override fun getText(): String = text

  override fun isRegex(): Boolean = false

  override fun matchesCase(): Boolean = isMatchCase

  override fun toString(): String {
    return "containing $text ${caseSensitiveText()}"
  }

  companion object {
    @JvmStatic
    fun createTextFilter(text: String, isRegexpAllowed: Boolean = false, isMatchCase: Boolean = false): VcsLogTextFilter {
      if (isRegexpAllowed && VcsLogUtil.maybeRegexp(text)) {
        try {
          return VcsLogRegexTextFilter(Pattern.compile(text, if (isMatchCase) 0 else Pattern.CASE_INSENSITIVE))
        }
        catch (ignored: PatternSyntaxException) {
        }
      }
      return VcsLogTextFilterImpl(text, isMatchCase)
    }

    @JvmStatic
    fun createTextFilter(patterns: List<String>, isMatchCase: Boolean = false): VcsLogTextFilter {
      if (patterns.isEmpty()) return createTextFilter("", false, isMatchCase)
      if (patterns.size == 1) return createTextFilter(patterns.single(), false, isMatchCase)
      return VcsLogMultiplePatternsTextFilter(patterns, isMatchCase)
    }
  }
}

class VcsLogRegexTextFilter internal constructor(private val pattern: Pattern) : VcsLogDetailsFilter, VcsLogTextFilter {
  override fun matches(message: String): Boolean = pattern.matcher(message).find()

  override fun getText(): String = pattern.pattern()

  override fun isRegex(): Boolean = true

  override fun matchesCase(): Boolean = (pattern.flags() and Pattern.CASE_INSENSITIVE) == 0

  override fun toString(): String {
    return "matching $text ${caseSensitiveText()}"
  }
}

class VcsLogMultiplePatternsTextFilter internal constructor(val patterns: List<String>,
                                                            private val isMatchCase: Boolean) : VcsLogDetailsFilter, VcsLogTextFilter {
  override fun getText(): String = if (patterns.size == 1) patterns.single() else patterns.joinToString("|") { Pattern.quote(it) }

  override fun isRegex(): Boolean = patterns.size > 1

  override fun matchesCase(): Boolean = isMatchCase

  override fun matches(message: String): Boolean = patterns.any { message.contains(it, !isMatchCase) }

  override fun toString(): String {
    return "containing at least one of the ${patterns.joinToString(", ")} ${caseSensitiveText()}"
  }

}

private fun VcsLogTextFilter.caseSensitiveText() = "(case ${if (matchesCase()) "sensitive" else "insensitive"})"