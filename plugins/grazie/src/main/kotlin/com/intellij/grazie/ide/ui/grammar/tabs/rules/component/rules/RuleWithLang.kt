// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.grammar.tabs.rules.component.rules

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.jlanguage.LangTool
import org.languagetool.rules.Category
import org.languagetool.rules.Rule
import java.util.*

internal data class RuleWithLang(val rule: Rule, val lang: Lang, val enabled: Boolean, var enabledInTree: Boolean) : Comparable<RuleWithLang> {
  override fun compareTo(other: RuleWithLang) = rule.description.compareTo(other.rule.description)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as RuleWithLang

    if (lang != other.lang) return false
    if (rule.description != other.rule.description) return false

    return true
  }

  override fun hashCode(): Int {
    var result = rule.description.hashCode()
    result = 31 * result + lang.hashCode()
    return result
  }
}

internal typealias RulesMap = Map<Lang, Map<ComparableCategory, SortedSet<RuleWithLang>>>

internal class ComparableCategory(val category: Category, val lang: Lang) : Comparable<ComparableCategory> {
  override fun compareTo(other: ComparableCategory) = category.name.compareTo(other.category.name)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ComparableCategory

    if (category != other.category) return false

    return true
  }

  override fun hashCode(): Int {
    return category.hashCode()
  }
}

internal fun LangTool.allRulesWithLangs(state: GrazieConfig.State = GrazieConfig.get()): RulesMap {
  val langs = state.enabledLanguages

  val result = TreeMap<Lang, SortedMap<ComparableCategory, SortedSet<RuleWithLang>>>(Comparator.comparing(Lang::nativeName))
  langs.filter { it.jLanguage != null }.forEach { lang ->
    val categories = TreeMap<ComparableCategory, SortedSet<RuleWithLang>>()

    with(getTool(lang)) {
      val activeRules = allActiveRules.toSet()

      fun Rule.isActive() = (id in state.userEnabledRules && id !in state.userDisabledRules)
                            || (id !in state.userDisabledRules && id !in state.userEnabledRules && this in activeRules)

      allRules.distinctBy { it.id }.forEach {
        if (!it.isDictionaryBasedSpellingRule) {
          categories.getOrPut(ComparableCategory(it.category, lang), ::TreeSet).add(
            RuleWithLang(it, lang, enabled = it.isActive(), enabledInTree = it.isActive()))
        }
      }

      if (categories.isNotEmpty()) result[lang] = categories
    }
  }

  return result
}
