package com.intellij.grazie.text

import ai.grazie.nlp.langs.Language
import com.intellij.grazie.rule.ParsedSentence
import com.intellij.grazie.text.TreeRuleChecker.TreeProblem
import java.util.*

class AsyncTreeRuleChecker : ExternalTextChecker() {
  override fun getRules(locale: Locale): Collection<Rule> {
    val language = Language.parse(locale.language) ?: return emptyList()
    return TreeRuleChecker.getRules(language)
  }

  override suspend fun checkExternally(context: ProofreadingContext): Collection<TreeProblem> {
    val sentences = ParsedSentence.getSentencesAsync(context)
    if (sentences.isEmpty()) return emptyList()

    return TreeRuleChecker.check(context.text, sentences)
  }
}