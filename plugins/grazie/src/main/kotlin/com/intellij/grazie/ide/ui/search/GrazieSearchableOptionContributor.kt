// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.search

import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.text.TextExtractor
import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.intellij.openapi.options.OptionsBundle

private class GrazieSearchableOptionContributor : SearchableOptionContributor() {
  private val proofreadId = "proofread"
  private val proofreadName = OptionsBundle.message("configurable.group.proofread.settings.display.name")

  private val grammarId = "reference.settingsdialog.project.grazie"
  private val grammarName = GraziePlugin.settingsPageName

  private fun SearchableOptionProcessor.addProofreadOptions(text: String, path: String? = null, hit: String? = text) {
    addOptions(text, path, hit, proofreadId, proofreadName, false)
  }

  private fun SearchableOptionProcessor.addGrammarOptions(text: String, path: String? = null, hit: String? = text) {
    addOptions(text, path, hit, grammarId, grammarName, false)
  }

  override fun processOptions(processor: SearchableOptionProcessor) {
    for (lang in Lang.values()) {
      processor.addProofreadOptions("${lang.displayName} ${lang.nativeName}", hit = msg("grazie.settings.proofreading.languages.text"))
    }
    for (language in TextExtractor.getSupportedLanguages()) {
      processor.addGrammarOptions(language.displayName, hit = msg("grazie.settings.grammar.scope.file-types.text"))
    }
    processor.addGrammarOptions("grazie", null, null)
  }
}
