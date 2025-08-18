package com.intellij.grazie.text

import ai.grazie.nlp.langs.Language
import com.intellij.grazie.rule.ParsedSentence
import com.intellij.grazie.text.TreeRuleChecker.TreeProblem
import com.intellij.grazie.utils.HighlightingUtil
import com.intellij.grazie.utils.blockingContext
import com.intellij.grazie.utils.isFunctionallyDisabled
import java.util.*

sealed class AsyncTreeRuleChecker : ExternalTextChecker() {

  override suspend fun checkExternally(content: TextContent): Collection<TreeProblem> {
    if (isFunctionallyDisabled()) return emptyList()
    if (HighlightingUtil.skipExpensivePrecommitAnalysis(content.containingFile)) return emptyList()

    val sentences = ParsedSentence.getSentencesAsync(content)
    if (sentences.isEmpty()) return emptyList()

    return blockingContext { TreeRuleChecker.check(content, sentences) }
  }

  class Grammar : AsyncTreeRuleChecker() {

    override fun getRules(locale: Locale): Collection<Rule> {
      val language = Language.parse(locale.language) ?: return emptyList()
      return TreeRuleChecker.getRules(language)
    }

    override suspend fun checkExternally(content: TextContent): Collection<TreeProblem> {
      return super.checkExternally(content).filter { !it.match.rule().isStyleLike && !it.concedeToOtherCheckers }
    }
  }

  class GrammarLowPriority : AsyncTreeRuleChecker() {
    override fun getRules(locale: Locale): Collection<Rule> = emptyList()

    override suspend fun checkExternally(content: TextContent): Collection<TreeProblem> {
      return super.checkExternally(content).filter { !it.match.rule().isStyleLike && it.concedeToOtherCheckers }
    }
  }

  class Style : AsyncTreeRuleChecker() {
    override fun getRules(locale: Locale): Collection<Rule> = emptyList()

    override suspend fun checkExternally(content: TextContent): Collection<TreeProblem> {
      return super.checkExternally(content).filter { it.match.rule().isStyleLike }
    }
  }
}