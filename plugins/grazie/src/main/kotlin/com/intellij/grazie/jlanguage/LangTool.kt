// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.jlanguage

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.jlanguage.broker.GrazieDynamicDataBroker
import com.intellij.grazie.jlanguage.filters.UppercaseMatchFilter
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.containers.ContainerUtil
import org.languagetool.JLanguageTool
import org.languagetool.ResultCache
import org.languagetool.rules.CategoryId
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

      //Fix problem with Authenticator installed by LT
      this.language.disambiguator
      Authenticator.setDefault(null)
    }
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
