package com.intellij.grazie.spellcheck

import ai.grazie.nlp.langs.Language
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.grazie.text.Rule
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextProblem
import com.intellij.grazie.utils.ijRange
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.startOffset
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy.getSpellcheckingStrategy
import com.intellij.spellchecker.util.SpellCheckerBundle
import java.util.function.Supplier

class TypoProblem : TextProblem {
  val word: String
  val isCloud: Boolean
  val range: TextRange get() = highlightRanges.first()
  /**
   * Note: Potentially expensive operation.
   * It's "free" to call it in case if [isCloud] is true, because it means that computation was already performed somewhere on the servers.
   */
  val fixes: Set<String> by lazy { fixesSupplier.get() }
  private val fixesSupplier: Supplier<Set<String>>

  constructor(text: TextContent, range: ai.grazie.text.TextRange, word: String, isCloud: Boolean, fixes: Supplier<Set<String>>) :
    this(text, range.ijRange(), word, isCloud, fixes)

  constructor(text: TextContent, range: TextRange, word: String, isCloud: Boolean, fixes: Supplier<Set<String>>) : super(SpellingRule, text, range) {
    this.word = word
    this.isCloud = isCloud
    this.fixesSupplier = fixes
  }

  override fun getShortMessage(): String = SpellCheckerBundle.message("typo.0.in.word", word)
  override fun getDescriptionTemplate(isOnTheFly: Boolean): @InspectionMessage String = shortMessage
  override fun isSpellingProblem(): Boolean = true
  override fun shouldSuppressInCodeLikeFragments(): Boolean = false
  override fun getSuggestions(): List<Suggestion> = fixes.map { Suggestion.replace(range, it) }
  override fun getFileHighlightRanges(): List<TextRange> = listOfNotNull(super.fileHighlightRanges.reduceOrNull(TextRange::union))

  override fun getCustomFixes(): List<LocalQuickFix> {
    val element = text.commonParent
    val fixes = if (isCloud) fixes else null
    val strategy = getSpellcheckingStrategy(element)
    val range = fileHighlightRanges.firstOrNull()?.shiftLeft(element.startOffset) ?: return emptyList()
    return if (strategy != null) {
      strategy.getRegularFixes(element, range, false, word, fixes).toList()
    } else {
      SpellcheckingStrategy
        .getDefaultRegularFixes(false, word, element, range, fixes)
        .toList()
    }
  }
}

object SpellingRule : Rule(
  "Grazie.Spelling",
  Language.UNKNOWN,
  SpellCheckerBundle.message("typo.severity.capitalized"),
  "Typos"
) {
  override fun getDescription(): String = SpellCheckerBundle.message("typo.severity.capitalized")
}
