package com.intellij.grazie.text

import ai.grazie.gec.model.problem.concedeToOtherGrammarCheckers
import ai.grazie.nlp.langs.Language
import com.intellij.grazie.rule.ParsedSentence
import com.intellij.grazie.text.TreeRuleChecker.TreeProblem
import java.util.*

sealed class AsyncTreeRuleChecker : ExternalTextChecker() {
  override suspend fun checkExternally(context: ProofreadingContext): Collection<TreeProblem> {
    val sentences = ParsedSentence.getSentencesAsync(context)
    if (sentences.isEmpty()) return emptyList()

    return TreeRuleChecker.check(context.text, sentences)
  }

  class Grammar : AsyncTreeRuleChecker() {

    override fun getRules(locale: Locale): Collection<Rule> {
      val language = Language.parse(locale.language) ?: return emptyList()
      return TreeRuleChecker.getRules(language)
    }

    override suspend fun checkExternally(context: ProofreadingContext): Collection<TreeProblem> {
      return super.checkExternally(context).filter { !it.isStyleLike && !concedeToOtherGrammarCheckers(it.source) }
    }
  }

  class GrammarLowPriority : AsyncTreeRuleChecker() {
    override fun getRules(locale: Locale): Collection<Rule> = emptyList()

    override suspend fun checkExternally(context: ProofreadingContext): Collection<TreeProblem> {
      return super.checkExternally(context).filter { !it.isStyleLike && !concedeToOtherGrammarCheckers(it.source) }
    }
  }

  class Style : AsyncTreeRuleChecker() {
    override fun getRules(locale: Locale): Collection<Rule> = emptyList()

    override suspend fun checkExternally(context: ProofreadingContext): Collection<TreeProblem> {
      return super.checkExternally(context).filter { it.isStyleLike }
    }
  }
}