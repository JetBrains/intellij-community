// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.spellcheck

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.detector.heuristics.rule.RuleFilter
import com.intellij.grazie.detector.utils.collections.emptyLinkedSet
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.utils.LinkedSet
import com.intellij.grazie.utils.toLinkedSet
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.ClassLoaderUtil
import org.languagetool.JLanguageTool
import org.languagetool.rules.spelling.SpellingCheckRule
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable

object GrazieSpellchecker : GrazieStateLifecycle {
  private const val MAX_SUGGESTIONS_COUNT = 3

  private val filter by lazy { RuleFilter.withAllBuiltIn() }
  private fun filterCheckers(word: String): Set<SpellerTool> {
    val checkers = this.checkers.value
    if (checkers.isEmpty()) return emptySet()

    val preferred = filter.filter(listOf(word)).preferred
    return checkers.filter { checker -> preferred.any { checker.lang.equalsTo(it) } }.toSet()
  }

  private val logger = LoggerFactory.getLogger(GrazieSpellchecker::class.java)

  data class SpellerTool(val tool: JLanguageTool, val lang: Lang, val speller: SpellingCheckRule, val suggestLimit: Int) {
    fun check(word: String): Boolean? = synchronized(speller) {
      if (word.isBlank()) return true

      ClassLoaderUtil.computeWithClassLoader<Boolean, Throwable>(GraziePlugin.classLoader) {
        if (speller.match(tool.getRawAnalyzedSentence(word)).isEmpty()) {
          if (!speller.isMisspelled(word)) true
          else {
            // if the speller does not return matches, but the word is still misspelled (not in the dictionary),
            // then this word was ignored by the rule (e.g. alien word), and we cannot be sure about its correctness
            // let's try adding a small change to a word to see if it's alien
            val mutated = word + word.last() + word.last()
            if (speller.match(tool.getRawAnalyzedSentence(mutated)).isEmpty()) null else true
          }
        } else false
      }
    }

    fun suggest(text: String): Set<String> = synchronized(speller) {
      ClassLoaderUtil.computeWithClassLoader<Set<String>, Throwable>(GraziePlugin.classLoader) {
        speller.match(tool.getRawAnalyzedSentence(text))
          .flatMap { match ->
            match.suggestedReplacements.map {
              text.replaceRange(match.fromPos, match.toPos, it)
            }
          }
          .take(suggestLimit).toSet()
      }
    }
  }

  @Volatile
  private var checkers: Lazy<LinkedSet<SpellerTool>> = initCheckers()

  private fun initCheckers() = lazy(LazyThreadSafetyMode.PUBLICATION) {
    GrazieConfig.get().availableLanguages.filterNot { it.isEnglish() }.mapNotNull { lang ->
      ProgressManager.checkCanceled()
      val tool = LangTool.getTool(lang)
      tool.allSpellingCheckRules.firstOrNull()?.let {
        SpellerTool(tool, lang, it, MAX_SUGGESTIONS_COUNT)
      }
    }.toLinkedSet()
  }

  override fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {
    checkers = initCheckers()
    LangTool.runAsync { checkers.value }
  }

  fun isCorrect(word: String): Boolean? {
    val myCheckers = filterCheckers(word)

    var isAlien = true
    myCheckers.forEach { speller ->
      when (speller.check(word)) {
        true -> return true
        false -> isAlien = false
      }
    }

    return if (isAlien) null else false
  }

  /**
   * Checks text for spelling mistakes.
   */
  fun getSuggestions(word: String): LinkedSet<String> {
    val filtered = filterCheckers(word)
    if (filtered.isEmpty()) return emptyLinkedSet()

    val indicator = EmptyProgressIndicator.notNullize(ProgressManager.getGlobalProgressIndicator())
    return ApplicationUtil.runWithCheckCanceled(Callable {
      filtered.map { speller ->
        indicator.checkCanceled()
        speller.suggest(word)
      }.flatten().toLinkedSet()
    }, indicator)
  }
}
