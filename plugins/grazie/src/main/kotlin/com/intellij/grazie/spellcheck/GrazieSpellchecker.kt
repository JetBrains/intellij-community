// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.spellcheck

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.detector.heuristics.rule.RuleFilter
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.utils.LinkedSet
import com.intellij.grazie.utils.toLinkedSet
import com.intellij.openapi.util.ClassLoaderUtil
import org.languagetool.JLanguageTool
import org.languagetool.rules.spelling.SpellingCheckRule
import org.languagetool.rules.spelling.hunspell.HunspellRule
import org.slf4j.LoggerFactory

object GrazieSpellchecker : GrazieStateLifecycle {
  private const val MAX_SUGGESTIONS_COUNT = 3

  private val filter by lazy { RuleFilter.withAllBuiltIn() }
  private fun filterCheckers(word: String): Set<SpellerTool> {
    if (checkers.isEmpty()) return emptySet()

    val preferred = filter.filter(listOf(word)).preferred
    return checkers.filter { checker -> preferred.any { checker.lang.equalsTo(it) } }.toSet()
  }

  private val logger = LoggerFactory.getLogger(GrazieSpellchecker::class.java)

  data class SpellerTool(val tool: JLanguageTool, val lang: Lang, val speller: SpellingCheckRule, val suggestLimit: Int) {
    fun check(word: String): Boolean = synchronized(speller) {
      ClassLoaderUtil.computeWithClassLoader<Boolean, Throwable>(GraziePlugin.classLoader) {
        !speller.isMisspelled(word) || !speller.isMisspelled(word.capitalize())
      }
    }

    fun suggest(text: String): Set<String> = synchronized(speller) {
      ClassLoaderUtil.computeWithClassLoader<Set<String>, Throwable>(GraziePlugin.classLoader) {
        speller.match(tool.getRawAnalyzedSentence(text))
          .flatMap { it.suggestedReplacements }
          .take(suggestLimit).toSet()
      }
    }
  }

  @Volatile
  private var checkers: LinkedSet<SpellerTool> = LinkedSet()

  override fun init(state: GrazieConfig.State) {
    checkers = state.availableLanguages.filterNot { it.isEnglish() }.mapNotNull { lang ->
      val tool = LangTool.getTool(lang, state)
      tool.allSpellingCheckRules.firstOrNull()?.let {
        SpellerTool(tool, lang, it, MAX_SUGGESTIONS_COUNT)
      }
    }.toLinkedSet()
  }

  override fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {
    init(newState)
  }

  private fun Throwable.isFromHunspellRuleInit(): Boolean {
    return stackTrace.any { it.className == HunspellRule::class.java.canonicalName && it.methodName == "init" }
  }

  private fun disableHunspellRuleInitialization(rule: SpellingCheckRule) {
    if (rule !is HunspellRule) return

    val field = HunspellRule::class.java.getDeclaredField("needsInit")
    if (field.trySetAccessible()) {
       field.set(rule, false)
    }
  }

  fun isCorrect(word: String): Boolean? {
    val myCheckers = filterCheckers(word)

    if (myCheckers.isEmpty()) return null

    return myCheckers.any { speller ->
      try {
        speller.check(word)
      }
      catch (t: Throwable) {
        if (t.isFromHunspellRuleInit()) {
          disableHunspellRuleInitialization(speller.speller)
        }

        logger.warn("Got exception during check for spelling mistakes by LanguageTool with word: $word", t)
        false
      }
    }
  }

  /**
   * Checks text for spelling mistakes.
   */
  fun getSuggestions(word: String): LinkedSet<String> {
    return filterCheckers(word).mapNotNull { speller ->
      try {
        speller.suggest(word)
      }
      catch (t: Throwable) {
        if (t.isFromHunspellRuleInit()) {
          disableHunspellRuleInitialization(speller.speller)
        }

        logger.warn("Got exception during suggest for spelling mistakes by LanguageTool with word: $word", t)
        null
      }
    }.flatten().toLinkedSet()
  }
}
