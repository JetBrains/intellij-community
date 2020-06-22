// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.jlanguage

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.jlanguage.broker.GrazieDynamicClassBroker
import com.intellij.grazie.jlanguage.broker.GrazieDynamicDataBroker
import com.intellij.util.containers.CollectionFactory
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.languagetool.JLanguageTool
import org.languagetool.rules.UppercaseMatchFilter
import java.util.concurrent.ConcurrentHashMap

object LangTool : GrazieStateLifecycle {
  private val langs: MutableMap<Lang, JLanguageTool> = ConcurrentHashMap()
  private val rulesToLanguages = CollectionFactory.createMap<String, MutableSet<Lang>>()

  init {
    JLanguageTool.dataBroker = GrazieDynamicDataBroker
    JLanguageTool.classBroker = GrazieDynamicClassBroker
  }

  val allRules: Set<String>
    get() = rulesToLanguages.keys

  fun getTool(lang: Lang, state: GrazieConfig.State = GrazieConfig.get()): JLanguageTool {
    require(lang.jLanguage != null) { "Trying to get LangTool for not available language" }

    return langs.computeIfAbsent(lang) {
      JLanguageTool(lang.jLanguage!!).apply {
        addMatchFilter(UppercaseMatchFilter())

        state.userDisabledRules.forEach { id -> disableRule(id) }
        state.userEnabledRules.forEach { id -> enableRule(id) }

        allRules.distinctBy { it.id }.onEach { rule ->
          rulesToLanguages.getOrPut(rule.id, ::ObjectOpenHashSet).add(lang)
        }
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
