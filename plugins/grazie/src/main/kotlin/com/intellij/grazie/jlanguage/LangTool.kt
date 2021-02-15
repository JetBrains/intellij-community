// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.jlanguage

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.jlanguage.broker.GrazieDynamicDataBroker
import com.intellij.grazie.jlanguage.filters.UppercaseMatchFilter
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.containers.CollectionFactory
import org.languagetool.JLanguageTool
import org.languagetool.broker.ClassBroker
import org.languagetool.rules.CategoryId
import java.net.Authenticator
import java.util.concurrent.ConcurrentHashMap

internal object LangTool : GrazieStateLifecycle {
  private val langs: MutableMap<Lang, JLanguageTool> = ConcurrentHashMap()
  private val rulesToLanguages = CollectionFactory.createSmallMemoryFootprintMap<String, MutableSet<Lang>>()

  init {
    JLanguageTool.setDataBroker(GrazieDynamicDataBroker)
    JLanguageTool.setClassBrokerBroker(object : ClassBroker {
      override fun forName(qualifiedName: String): Class<*> {
        return GrazieDynamic.loadClass(qualifiedName) ?: throw ClassNotFoundException(qualifiedName)
      }
    })
  }

  val allRules: Set<String>
    get() = rulesToLanguages.keys

  fun getTool(lang: Lang, state: GrazieConfig.State = GrazieConfig.get()): JLanguageTool {
    require(lang.jLanguage != null) { "Trying to get LangTool for not available language" }

    return langs.computeIfAbsent(lang) {
      JLanguageTool(lang.jLanguage!!).apply {
        setCheckCancelledCallback { ProgressManager.checkCanceled(); false }
        addMatchFilter(UppercaseMatchFilter())

        state.userDisabledRules.forEach { id -> disableRule(id) }
        state.userEnabledRules.forEach { id -> enableRule(id) }

        fun loadConfigFile(path: String, block: (iso: String, id: String) -> Unit) {
          GrazieDynamicDataBroker.getFromResourceDirAsStream(path).use { stream ->
            stream.bufferedReader().forEachLine {
              val (iso, id) = it.split(':')
              block(iso, id)
            }
          }
        }

        loadConfigFile("en/enabled_rules.txt") { iso, ruleId ->
          if (iso == lang.iso.name && ruleId !in state.userDisabledRules) {
            enableRule(ruleId)
          }
        }

        loadConfigFile("en/disabled_rules.txt") { iso, ruleId ->
          if (iso == lang.iso.name && ruleId !in state.userEnabledRules) {
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

        allRules.distinctBy { it.id }.onEach { rule ->
          rulesToLanguages.getOrPut(rule.id, {CollectionFactory.createSmallMemoryFootprintSet()}).add(lang)
        }

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
    rulesToLanguages.clear()

    init(newState)
  }

  fun getRuleLanguages(ruleId: String) = rulesToLanguages[ruleId]
}
