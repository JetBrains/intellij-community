// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar.strategy.impl

import com.intellij.grazie.utils.LinkedSet

@Suppress("DEPRECATION")
@Deprecated(
  replaceWith = ReplaceWith("com.intellij.grazie.text.RuleGroup"),
  message = "Use a non-impl class"
)
class RuleGroup(rules: LinkedSet<String>): com.intellij.grazie.text.RuleGroup(rules) {
  constructor(vararg rules: String) : this(LinkedSet<String>(rules.toSet()))

  companion object {
    val EMPTY = RuleGroup()

    /** Rule for checking double whitespaces */
    @Deprecated("Use getStealthyRanges() in GrammarCheckingStrategy and StrategyUtils.indentIndexes()")
    val WHITESPACES = RuleGroup("WHITESPACE_RULE")

    /** Rules for checking casing errors */
    private val CASING = RuleGroup("UPPERCASE_SENTENCE_START")

    /** Rules for checking punctuation errors */
    private val PUNCTUATION = RuleGroup("PUNCTUATION_PARAGRAPH_END", "UNLIKELY_OPENING_PUNCTUATION")

    /** Rules that are usually disabled for literal strings */
    val LITERALS = CASING + PUNCTUATION
  }

  fun getRules(): LinkedSet<String> = LinkedSet(rules)

  operator fun plus(other: RuleGroup) = RuleGroup((rules + other.rules) as LinkedSet<String>)
}
