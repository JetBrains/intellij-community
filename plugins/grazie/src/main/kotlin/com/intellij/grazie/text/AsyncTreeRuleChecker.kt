package com.intellij.grazie.text

import ai.grazie.nlp.langs.Language
import com.intellij.grazie.rule.ParsedSentence
import com.intellij.grazie.utils.hasLanguage
import java.util.Locale

class AsyncTreeRuleChecker : TextChecker() {
  override fun getRules(locale: Locale): Collection<Rule> {
    val language = Language.parse(locale.language) ?: return emptyList()
    return TreeRuleChecker.getRules(language)
  }

  // Used by ReSharper
  @Deprecated("Use check(contexts) instead")
  override fun check(context: ProofreadingContext): Collection<TextProblem> {
    if (!context.hasLanguage()) return emptyList()
    val sentences = ParsedSentence.getSentences(context)
    if (sentences.isEmpty()) return emptyList()

    return TreeRuleChecker.check(context.text, sentences)
  }

  override fun check(contexts: List<ProofreadingContext>): Collection<TextProblem> {
    if (contexts.isEmpty()) return emptyList()
    return TreeRuleChecker.checkText(ParsedSentence.getAllCheckedSentences(contexts))
  }
}