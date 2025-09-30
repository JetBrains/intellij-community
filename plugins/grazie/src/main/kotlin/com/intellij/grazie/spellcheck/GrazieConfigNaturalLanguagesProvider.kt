package com.intellij.grazie.spellcheck

import ai.grazie.nlp.langs.Language
import com.intellij.grazie.GrazieConfig
import com.intellij.spellchecker.grazie.NaturalLanguagesProvider

class GrazieConfigNaturalLanguagesProvider : NaturalLanguagesProvider {
  override fun getEnabledLanguages(): Set<Language> =
    GrazieConfig.get().enabledLanguages
      .mapNotNull { lang -> Language.entries.find { lang.iso == it.iso } }
      .toSet()
}