// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.jlanguage

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.detection.LangDetector
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.jlanguage.broker.GrazieDynamicDataBroker
import com.intellij.grazie.jlanguage.filters.UppercaseMatchFilter
import com.intellij.grazie.jlanguage.hunspell.LuceneHunspellDictionary
import com.intellij.grazie.utils.text
import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.concurrency.SequentialTaskExecutor
import com.intellij.util.containers.ContainerUtil
import org.apache.commons.text.similarity.LevenshteinDistance
import org.languagetool.JLanguageTool
import org.languagetool.ResultCache
import org.languagetool.Tag
import org.languagetool.rules.CategoryId
import org.languagetool.rules.IncorrectExample
import org.languagetool.rules.spelling.hunspell.Hunspell
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object LangTool : GrazieStateLifecycle {
  private val langs: MutableMap<Lang, JLanguageTool> = Collections.synchronizedMap(ContainerUtil.createSoftValueMap())
  private val rulesEnabledByDefault: MutableMap<Lang, Set<String>> = ConcurrentHashMap()
  private val executor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("LangTool")

  init {
    JLanguageTool.useCustomPasswordAuthenticator(false)
    JLanguageTool.setDataBroker(GrazieDynamicDataBroker)
    JLanguageTool.setClassBrokerBroker { qualifiedName ->
      GrazieDynamic.loadClass(qualifiedName) ?: throw ClassNotFoundException(qualifiedName)
    }

    Hunspell.setHunspellDictionaryFactory(::LuceneHunspellDictionary)
  }

  internal fun globalIdPrefix(lang: Lang): String = "LanguageTool." + lang.remote.iso.name + "."

  @Suppress("UNUSED_PARAMETER", "DeprecatedCallableAddReplaceWith")
  @Deprecated("use the other overload")
  fun getTool(lang: Lang, state: GrazieConfig.State): JLanguageTool {
    return getTool(lang)
  }

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
        if (rule.hasTag(Tag.picky) && rule.id !in enabledRules) {
          disableRule(rule.id)
        }
      }

      for (rule in allRules) {
        ProgressManager.checkCanceled()
        rule.correctExamples = emptyList()
        rule.errorTriggeringExamples = emptyList()
        rule.incorrectExamples = removeVerySimilarExamples(rule.incorrectExamples)
      }

      this.language.disambiguator
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

  override fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {
    if (
      prevState.availableLanguages == newState.availableLanguages
      && prevState.userDisabledRules == newState.userDisabledRules
      && prevState.userEnabledRules == newState.userEnabledRules
    ) return

    langs.clear()
    rulesEnabledByDefault.clear()

    preloadAsync()
  }

  private fun preloadAsync() {
    runAsync {
      LangDetector.getLanguage("Hello")
      GrazieConfig.get().availableLanguages.forEach { getTool(it) }
    }
  }

  internal fun runAsync(action: Runnable) {
    executor.execute {
      try {
        action.run()
      }
      catch (ignore: AlreadyDisposedException) { }
    }
  }

  internal class Preloader : PreloadingActivity() {
    override fun preload(indicator: ProgressIndicator): Unit = preloadAsync()
  }
}
