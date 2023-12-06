// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.search

import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.text.TextExtractor
import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.progress.ProgressManager

private class GrazieSearchableOptionContributor : SearchableOptionContributor() {
  private val proofreadId = "proofread"
  private val proofreadName = OptionsBundle.message("configurable.group.proofread.settings.display.name")

  private val grammarId = "reference.settingsdialog.project.grazie"
  private val grammarName = GraziePlugin.settingsPageName

  private fun SearchableOptionProcessor.addProofreadOptions(text: String, path: String? = null, hit: String? = text) {
    ProgressManager.checkCanceled()
    addOptions(text, path, hit, proofreadId, proofreadName, false)
  }

  private fun SearchableOptionProcessor.addGrammarOptions(text: String, path: String? = null, hit: String? = text) {
    ProgressManager.checkCanceled()
    addOptions(text, path, hit, grammarId, grammarName, false)
  }

  override fun processOptions(processor: SearchableOptionProcessor) {
    val languageProofreadingHit = msg("grazie.settings.proofreading.languages.text")
    GrazieStaticSearchableOptions.LanguageProofreading.process { option ->
      processor.addProofreadOptions(option, hit = languageProofreadingHit)
    }
    val fileTypeHit = msg("grazie.settings.grammar.scope.file-types.text")
    for (language in TextExtractor.getSupportedLanguages()) {
      processor.addGrammarOptions(language.displayName, hit = fileTypeHit)
    }
    processor.addGrammarOptions("grazie", null, null)
    val ruleHit = msg("grazie.settings.grammar.scope.rules.text")
    GrazieStaticSearchableOptions.Rules.process { option ->
      processor.addGrammarOptions(option, hit = ruleHit)
    }
    GrazieStaticSearchableOptions.RuleCategories.process { option ->
      processor.addGrammarOptions(option, hit = ruleHit)
    }
  }
}
