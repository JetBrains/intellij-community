// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.jlanguage

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.jlanguage.broker.GrazieDynamicDataBroker
import com.intellij.grazie.jlanguage.filters.UppercaseMatchFilter
import com.intellij.openapi.progress.ProgressManager
import org.languagetool.JLanguageTool
import org.languagetool.rules.CategoryId
import java.net.Authenticator
import java.util.concurrent.ConcurrentHashMap

internal object LangTool : GrazieStateLifecycle {
  private val langs: MutableMap<CacheKey, JLanguageTool> = ConcurrentHashMap()

  private data class CacheKey(val lang: Lang, val disabledRules: Set<String>, val enabledRules: Set<String>)

  init {
    JLanguageTool.setDataBroker(GrazieDynamicDataBroker)
    JLanguageTool.setClassBrokerBroker { qualifiedName ->
      GrazieDynamic.loadClass(qualifiedName) ?: throw ClassNotFoundException(qualifiedName)
    }
  }

  fun getTool(lang: Lang, state: GrazieConfig.State = GrazieConfig.get()): JLanguageTool {
    require(lang.jLanguage != null) { "Trying to get LangTool for not available language" }

    val key = CacheKey(lang, state.userDisabledRules, state.userEnabledRules)

    return langs.computeIfAbsent(key) {
      JLanguageTool(lang.jLanguage!!).apply {
        setCheckCancelledCallback { ProgressManager.checkCanceled(); false }
        addMatchFilter(UppercaseMatchFilter())

        key.disabledRules.forEach { id -> disableRule(id) }
        key.enabledRules.forEach { id -> enableRule(id) }

        fun loadConfigFile(path: String, block: (iso: String, id: String) -> Unit) {
          GrazieDynamicDataBroker.getFromResourceDirAsStream(path).use { stream ->
            stream.bufferedReader().forEachLine {
              val (iso, id) = it.split(':')
              block(iso, id)
            }
          }
        }

        loadConfigFile("en/enabled_rules.txt") { iso, ruleId ->
          if (iso == lang.iso.name && ruleId !in key.disabledRules) {
            enableRule(ruleId)
          }
        }

        loadConfigFile("en/disabled_rules.txt") { iso, ruleId ->
          if (iso == lang.iso.name && ruleId !in key.enabledRules) {
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

    init(newState)
  }
}
