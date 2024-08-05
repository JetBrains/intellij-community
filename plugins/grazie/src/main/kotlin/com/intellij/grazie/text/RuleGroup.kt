package com.intellij.grazie.text

import org.jetbrains.annotations.ApiStatus
import java.util.*

/** A user-defined set of rule ids or strings denoting abstract categories of rules. */
@ApiStatus.NonExtendable
open class RuleGroup(rules: Set<String>) {
  constructor(vararg rules: String) : this(rules.toSet())

  val rules: Set<String> = Collections.unmodifiableSet(rules)

  @Suppress("MemberVisibilityCanBePrivate")
  companion object {
    /** An abstract category for all rules that warn that sentences should be capitalized */
    const val SENTENCE_START_CASE = "UPPERCASE_SENTENCE_START"

    /**
     * An abstract category for all rules that warn that neutral sentences (neither questions nor exclamations)
     * should end with some punctuation (e.g. a dot)
     */
    const val SENTENCE_END_PUNCTUATION = "PUNCTUATION_PARAGRAPH_END"

    /** An abstract category for all rules that report sentences starting with an unexpected punctuation (e.g. a semicolon). */
    const val UNLIKELY_OPENING_PUNCTUATION = "UNLIKELY_OPENING_PUNCTUATION"

    /** An abstract category for all rules that report sentence fragments (e.g. bare noun phrases or missing subject). */
    const val INCOMPLETE_SENTENCE = "INCOMPLETE_SENTENCE"

    /** An abstract category for all rules that report sentences not separated properly (e.g. missing space or lowercase after dot). */
    const val UNDECORATED_SENTENCE_SEPARATION = "UNDECORATED_SENTENCE_SEPARATION"

    val EMPTY = RuleGroup()

    /** Rules for checking casing errors */
    @JvmField
    val CASING = RuleGroup(SENTENCE_START_CASE)

    /**
     * Rules for checking for incorrect punctuation at sentence beginning or end.
     * This group consists of [SENTENCE_END_PUNCTUATION] and [UNLIKELY_OPENING_PUNCTUATION].
     */
    @JvmField
    val SENTENCE_BOUNDARY_PUNCTUATION = RuleGroup(SENTENCE_END_PUNCTUATION, UNLIKELY_OPENING_PUNCTUATION)

    /** Rules for checking punctuation errors */
    @JvmField
    @Deprecated("use SENTENCE_BOUNDARY", ReplaceWith("SENTENCE_BOUNDARY_PUNCTUATION"))
    val PUNCTUATION = SENTENCE_BOUNDARY_PUNCTUATION

    /** Rules that are usually disabled for literal strings */
    @JvmField
    val LITERALS = CASING + SENTENCE_BOUNDARY_PUNCTUATION

    /**
     * Rules that allow for single sentences to be lowercase and lack starting/finishing punctuation,
     * useful in comments or commit messages
     */
    @JvmField
    val UNDECORATED_SINGLE_SENTENCE = CASING + SENTENCE_BOUNDARY_PUNCTUATION
  }

  operator fun plus(other: RuleGroup) = RuleGroup(rules + other.rules)

  override fun equals(other: Any?): Boolean = this === other || other is RuleGroup && rules == other.rules
  override fun hashCode(): Int = rules.hashCode()
  override fun toString(): String = rules.toString()
}
