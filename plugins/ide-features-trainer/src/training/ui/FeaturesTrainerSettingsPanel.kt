// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*
import training.lang.LangManager
import training.learn.LearnBundle
import training.util.resetPrimaryLanguage
import javax.swing.DefaultComboBoxModel

class FeaturesTrainerSettingsPanel : BoundConfigurable(LearnBundle.message("learn.options.panel.name"), null) {
  override fun createPanel(): DialogPanel = panel {
    val languagesExtensions = LangManager.getInstance().supportedLanguagesExtensions.sortedBy { it.language }
    if (languagesExtensions.isNotEmpty()) {
      row {
        label(LearnBundle.message("learn.option.main.language"))
        val options = languagesExtensions.mapNotNull { Language.findLanguageByID(it.language) }
          .map { LanguageOption(it) }.toTypedArray()
        comboBox<LanguageOption>(DefaultComboBoxModel(options), {
          val languageName = LangManager.getInstance().state.languageName
          options.find { it.id == languageName } ?: options[0]
        }, { language ->
                             val chosen = languagesExtensions.first { it.language == language?.id }
                             resetPrimaryLanguage(chosen.instance)
                           })
      }
    }
    row {
      buttonFromAction(LearnBundle.message("learn.option.reset.progress"), "settings", ActionManager.getInstance().getAction("ResetLearningProgressAction"))
    }
  }

  private data class LanguageOption(val language: Language) {
    override fun toString(): String = language.displayName
    val id get() = language.id
  }
}
