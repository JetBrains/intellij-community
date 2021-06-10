// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.jlanguage

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.jlanguage.broker.GrazieDynamicDataBroker
import com.intellij.grazie.jlanguage.filters.UppercaseMatchFilter
import com.intellij.grazie.utils.text
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.containers.ContainerUtil
import org.apache.commons.text.similarity.LevenshteinDistance
import org.languagetool.JLanguageTool
import org.languagetool.ResultCache
import org.languagetool.Tag
import org.languagetool.rules.CategoryId
import org.languagetool.rules.IncorrectExample
import java.net.Authenticator
import java.util.concurrent.ConcurrentHashMap

internal object LangTool : GrazieStateLifecycle {
  private val langs: MutableMap<Lang, JLanguageTool> = ContainerUtil.createConcurrentSoftValueMap()
  private val rulesEnabledByDefault: MutableMap<Lang, Set<String>> = ConcurrentHashMap()

  init {
    JLanguageTool.setDataBroker(GrazieDynamicDataBroker)
    JLanguageTool.setClassBrokerBroker { qualifiedName ->
      GrazieDynamic.loadClass(qualifiedName) ?: throw ClassNotFoundException(qualifiedName)
    }
  }

  internal fun globalIdPrefix(lang: Lang): String = "LanguageTool." + lang.remote.iso.name + "."

  /**
   * @param state this should always be the current state in [GrazieConfig].
   * It's a parameter for internal reasons, to avoid cyclic service initialization.
   */
  fun getTool(lang: Lang, state: GrazieConfig.State = GrazieConfig.get()): JLanguageTool {
    return langs.computeIfAbsent(lang) { createTool(lang, state) }
  }

  private fun createTool(lang: Lang, state: GrazieConfig.State): JLanguageTool {
    val jLanguage = lang.jLanguage
    require(jLanguage != null) { "Trying to get LangTool for not available language" }
    return JLanguageTool(jLanguage, null, ResultCache(1_000)).apply {
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
        if (rule.hasTag(Tag.picky) && rule.id !in enabledRules) {
          disableRule(rule.id)
        }
      }

      for (rule in allRules) {
        rule.correctExamples = emptyList()
        rule.errorTriggeringExamples = emptyList()
        rule.incorrectExamples = removeVerySimilarExamples(rule.incorrectExamples)
      }

      //Fix problem with Authenticator installed by LT
      this.language.disambiguator
      Authenticator.setDefault(null)
    }
  }

  private fun removeVerySimilarExamples(examples: List<IncorrectExample>): List<IncorrectExample> {
    val accepted = ArrayList<IncorrectExample>()
    for (example in examples) {
      if (accepted.none { it.text.isSimilarTo(example.text) }) {
        val firstCorrection = example.corrections.find { it.isNotBlank() }
        accepted.add(IncorrectExample(example.example, ContainerUtil.createMaybeSingletonList(firstCorrection)))
        if (accepted.size > 5) break
      }
    }
    return accepted
  }

  private const val MINIMUM_EXAMPLE_SIMILARITY = 0.2
  private val levenshtein = LevenshteinDistance()

  private fun CharSequence.isSimilarTo(sequence: CharSequence): Boolean {
    return levenshtein.apply(this, sequence).toDouble() / length < MINIMUM_EXAMPLE_SIMILARITY
  }

  internal fun isRuleEnabledByDefault(lang: Lang, ruleId: String): Boolean {
    val activeIds = rulesEnabledByDefault.computeIfAbsent(lang) {
      createTool(lang, GrazieConfig.State()).allActiveRules.map { it.id }.toSet()
    }
    return activeIds.contains(ruleId)
  }

  override fun init(state: GrazieConfig.State) {
    // Creating LanguageTool for each language
    state.availableLanguages.forEach { getTool(it, state) }
  }

  override fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {
    if (
      prevState.availableLanguages == newState.availableLanguages
      && prevState.userDisabledRules == newState.userDisabledRules
      && prevState.userEnabledRules == newState.userEnabledRules
    ) return

    langs.clear()
    rulesEnabledByDefault.clear()

    init(newState)
  }
}
