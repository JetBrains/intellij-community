package com.intellij.grazie.spellcheck

import ai.grazie.nlp.langs.Language
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.grazie.text.Rule
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextProblem
import com.intellij.grazie.utils.ijRange
import com.intellij.openapi.util.TextRange
import com.intellij.spellchecker.util.SpellCheckerBundle
import java.util.function.Supplier

class TypoProblem : TextProblem {
  val word: String
  val isCloud: Boolean
  private val fixesSupplier: Supplier<Set<String>>

  constructor(text: TextContent, range: ai.grazie.text.TextRange, word: String, isCloud: Boolean, fixes: Supplier<Set<String>>) :
    this(text, range.ijRange(), word, isCloud, fixes)

  constructor(text: TextContent, range: TextRange, word: String, isCloud: Boolean, fixes: Supplier<Set<String>>) : super(SpellingRule, text, range) {
    this.word = word
    this.isCloud = isCloud
    this.fixesSupplier = fixes
  }

  override fun getShortMessage(): String = SpellCheckerBundle.message("typo.in.word.ref", word)

  override fun getDescriptionTemplate(isOnTheFly: Boolean): @InspectionMessage String = shortMessage

  override fun getSuggestions(): List<Suggestion> = fixes.map { Suggestion.replace(highlightRanges.first(), it) }

  /**
   * Note: Potentially expensive operation.
   * It's "free" to call it in case if [isCloud] is true, because it means that computation was already performed somewhere in on the servers.
   */
  val fixes: Set<String> by lazy { fixesSupplier.get() }

  val range: TextRange get() = highlightRanges.first()

  override fun isSpellingProblem(): Boolean = true
}

object SpellingRule : Rule(
  "Grazie.Spelling",
  Language.UNKNOWN,
  SpellCheckerBundle.message("typo.severity.capitalized"),
  "Typos"
) {
  override fun getDescription(): String = SpellCheckerBundle.message("typo.severity.capitalized")
}
