// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui

import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.jlanguage.Lang
import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor

class GrazieSearchableOptionContributor : SearchableOptionContributor() {
  private val id = "reference.settingsdialog.project.grazie"
  private val displayName = "Grazie"

  private fun SearchableOptionProcessor.addOptions(text: String, path: String, hit: String = text) {
    addOptions(text, path, hit, id, displayName, true)
  }

  override fun processOptions(processor: SearchableOptionProcessor) {
    with(processor) {
      addOptions(msg("grazie.ui.settings.vcs.enable.text"), msg("grazie.ui.settings.vcs.enable.text"))
      addOptions(msg("grazie.ui.settings.rules.configuration.text"), msg("grazie.ui.settings.rules.configuration.text"))
      addOptions(msg("grazie.ui.settings.languages.native.text"), msg("grazie.ui.settings.languages.native.text"))

      Lang.values().forEach { lang ->
        addOptions(lang.displayName, msg("grazie.ui.settings.languages.text"))
      }
    }
  }
}
