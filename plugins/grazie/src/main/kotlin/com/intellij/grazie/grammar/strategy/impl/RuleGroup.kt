// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar.strategy.impl

import com.intellij.grazie.utils.LinkedSet

/**
 * A group of Grazie grammar rules.
 *
 * This class represent a user-defined set of rules.
 */
data class RuleGroup(val rules: LinkedSet<String>) {
  constructor(vararg rules: String) : this(LinkedSet(rules.toSet()))

  companion object {
    val EMPTY = RuleGroup()

    /** Rule for checking double whitespaces */
    @Deprecated("Use getStealthyRanges() in GrammarCheckingStrategy and StrategyUtils.indentIndexes()")
    val WHITESPACES = RuleGroup("WHITESPACE_RULE")

    /** Rules for checking casing errors */
    val CASING = RuleGroup("UPPERCASE_SENTENCE_START")

    /** Rules for checking punctuation errors */
    val PUNCTUATION = RuleGroup("PUNCTUATION_PARAGRAPH_END", "UNLIKELY_OPENING_PUNCTUATION")

    /** Rules that are usually disabled for literal strings */
    val LITERALS = CASING + PUNCTUATION
  }

  operator fun plus(other: RuleGroup) = RuleGroup((rules + other.rules) as LinkedSet<String>)
}
