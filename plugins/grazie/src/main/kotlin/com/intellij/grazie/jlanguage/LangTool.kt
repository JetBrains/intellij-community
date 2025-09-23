// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.jlanguage

import ai.grazie.nlp.similarity.Levenshtein
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.jlanguage.broker.GrazieDynamicDataBroker
import com.intellij.grazie.jlanguage.filters.UppercaseMatchFilter
import com.intellij.grazie.jlanguage.hunspell.LuceneHunspellDictionary
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.ContainerUtil
import org.apache.commons.text.similarity.LevenshteinDistance
import org.languagetool.JLanguageTool
import org.languagetool.ResultCache
import org.languagetool.Tag
import org.languagetool.rules.CategoryId
import org.languagetool.rules.ExampleSentence
import org.languagetool.rules.IncorrectExample
import org.languagetool.rules.Rule
import org.languagetool.rules.patterns.AbstractPatternRule
import org.languagetool.rules.patterns.PatternToken
import org.languagetool.rules.patterns.RepeatedPatternRuleTransformer
import org.languagetool.rules.spelling.hunspell.Hunspell
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object LangTool : GrazieStateLifecycle {
  private val langs: MutableMap<Lang, JLanguageTool> = Collections.synchronizedMap(ContainerUtil.createSoftValueMap())
  private val rulesEnabledByDefault = ConcurrentHashMap<Lang, Set<String>>()
  private val inappropriateExamples = mapOf(
    "DT_NNS_AGREEMENT" to setOf("small children and their mothers", "eternal rest")
  )

  init {
    JLanguageTool.useCustomPasswordAuthenticator(false)
    JLanguageTool.setDataBroker(GrazieDynamicDataBroker)
    JLanguageTool.setClassBrokerBroker { qualifiedName ->
      GrazieDynamic.loadClass(qualifiedName) ?: throw ClassNotFoundException(qualifiedName)
    }

    Hunspell.setHunspellDictionaryFactory(::LuceneHunspellDictionary)
  }

  internal fun globalIdPrefix(lang: Lang): String = "LanguageTool." + lang.ltRemote!!.iso.name + "."

  fun getTool(lang: Lang): JLanguageTool {
    // this is equivalent to computeIfAbsent, but allows multiple threads to create tools concurrently,
    // so that threads can be interrupted (with checkCanceled on their own indicator) instead of waiting on a lock
    while (true) {
      var tool = langs[lang]
      if (tool != null) return tool

      val state = GrazieConfig.get()
      tool = createTool(lang, state)
      synchronized(langs) {
        if (state === GrazieConfig.get()) {
          val alreadyComputed = langs[lang]
          if (alreadyComputed != null) return alreadyComputed

          langs[lang] = tool
          return tool
        }
      }
    }
  }

  internal fun createTool(lang: Lang, state: GrazieConfig.State): JLanguageTool {
    val jLanguage = lang.jLanguage
    require(jLanguage != null) { "Trying to get LangTool for not available language" }
    return JLanguageTool(jLanguage, null, ResultCache(10_000)).apply {
      setCheckCancelledCallback { ProgressManager.checkCanceled(); false }
      addMatchFilter(UppercaseMatchFilter())

      val prefix = globalIdPrefix(lang)
      val disabledRules = state.userDisabledRules.mapNotNull { if (it.startsWith(prefix)) it.substring(prefix.length) else null }.toSet()
      val enabledRules =  state.userEnabledRules.mapNotNull  { if (it.startsWith(prefix)) it.substring(prefix.length) else null }.toSet()

      disabledRules.forEach { id -> disableRule(id) }
      enabledRules.forEach { id -> enableRule(id) }

      fun loadConfigFile(path: String, block: (iso: String, id: String) -> Unit) {
        GrazieDynamicDataBroker.getFromResourceDirAsStream(path).use { stream ->
          stream.bufferedReader().forEachLine {
            val (iso, id) = it.split(':')
            block(iso, id)
          }
        }
      }

      loadConfigFile("en/enabled_rules.txt") { iso, ruleId ->
        if (iso == lang.iso.name && ruleId !in disabledRules) {
          enableRule(ruleId)
        }
      }

      loadConfigFile("en/disabled_rules.txt") { iso, ruleId ->
        if (iso == lang.iso.name && ruleId !in enabledRules) {
          disableRule(ruleId)
        }
      }

      loadConfigFile("en/enabled_categories.txt") { iso, categoryId ->
        if (iso == lang.iso.name) {
          enableRuleCategory(CategoryId(categoryId))
        }
      }

      loadConfigFile("en/disabled_categories.txt") { iso, categoryId ->
        if (iso == lang.iso.name) {
          disableCategory(CategoryId(categoryId))
        }
      }

      allSpellingCheckRules.forEach { rule -> disableRule(rule.id) }

      for (rule in allActiveRules) {
        if (rule.isPicky() && rule.id !in enabledRules) {
          disableRule(rule.id)
        }
      }

      for (rule in allRules) {
        ProgressManager.checkCanceled()
        rule.correctExamples = emptyList()
        rule.errorTriggeringExamples = emptyList()
        rule.incorrectExamples = removeVerySimilarExamples(rule)
        if (Lang.shouldDisableChunker(jLanguage)) {
          prepareForNoChunkTags(rule)
        }
      }
    }
  }

  private fun Rule.isPicky(): Boolean {
    // Workaround for https://github.com/languagetool-org/languagetool/issues/11376
    if (this.hasTag(Tag.picky)) return true

    if (this is RepeatedPatternRuleTransformer.RepeatedPatternRule) {
      return this.wrappedRules.all { it.hasTag(Tag.picky) }
    }
    return false
  }

  private fun prepareForNoChunkTags(rule: Rule) {
    @Suppress("TestOnlyProblems")
    fun relaxChunkConditions(token: PatternToken, positive: Boolean) {
      if (token.negation == positive && token.chunkTag != null) {
        token.chunkTag = null
      }

      token.andGroup.forEach { relaxChunkConditions(it, positive) }
      token.orGroup.forEach { relaxChunkConditions(it, positive) }
      (token.exceptionList ?: emptyList()).forEach { relaxChunkConditions(it, !positive) }
    }

    if (rule is AbstractPatternRule) {
      rule.patternTokens?.forEach { relaxChunkConditions(it, positive = true) }
    }

    rule.antiPatterns.forEach { it.patternTokens?.forEach { token -> relaxChunkConditions(token, positive = false) } }
  }

  private fun removeVerySimilarExamples(rule: Rule): List<IncorrectExample> {
    val cleanAccepted = ArrayList<String>()
    val accepted = ArrayList<IncorrectExample>()
    for (example in removeInappropriateExamples(rule)) {
      val cleanExample = ExampleSentence.cleanMarkersInExample(example.example)
      if (cleanAccepted.none { it.isSimilarTo(cleanExample) }) {
        val corrections = example.corrections.filter { it.isNotBlank() }.take(3)
        accepted.add(IncorrectExample(example.example, corrections))
        cleanAccepted.add(cleanExample)
        if (accepted.size > 5) break
      }
    }
    return accepted
  }

  private fun removeInappropriateExamples(rule: Rule): List<IncorrectExample> {
    val inappropriateExamples = inappropriateExamples[rule.id]
    if (inappropriateExamples != null) {
      return rule.incorrectExamples
        .filterNot { example -> inappropriateExamples.any { example.example.contains(it)} }
    }
    return rule.incorrectExamples
  }

  private const val MINIMUM_EXAMPLE_SIMILARITY = 0.2

  private fun CharSequence.isSimilarTo(sequence: CharSequence): Boolean {
    val maxLength = max(this.length, sequence.length)
    val distance = ceil(maxLength * MINIMUM_EXAMPLE_SIMILARITY).toInt()
    if (abs(this.length - sequence.length) > distance) return false

    val prefixLength = StringUtil.commonPrefixLength(this, sequence)
    val suffixLength = StringUtil.commonSuffixLength(this, sequence)
    return suffixLength + prefixLength >= distance
  }

  internal fun isRuleEnabledByDefault(lang: Lang, ruleId: String): Boolean {
    val activeIds = rulesEnabledByDefault.computeIfAbsent(lang) {
      createTool(lang, GrazieConfig.State()).allActiveRules.map { it.id }.toSet()
    }
    return activeIds.contains(ruleId)
  }

  override fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {
    if (
      prevState.availableLanguages == newState.availableLanguages
      && prevState.userDisabledRules == newState.userDisabledRules
      && prevState.userEnabledRules == newState.userEnabledRules
    ) return

    langs.clear()
    rulesEnabledByDefault.clear()
  }
}
