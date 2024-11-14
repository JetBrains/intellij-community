// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.Language
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import training.lang.LangManager
import training.learn.CourseManager
import training.learn.LearnBundle
import training.statistic.StatisticBase
import training.util.SHOW_NEW_LESSONS_NOTIFICATION
import training.util.getActionById
import training.util.resetPrimaryLanguage

private class FeaturesTrainerSettingsPanel : BoundConfigurable(LearnBundle.message("learn.options.panel.name"), null) {
  override fun createPanel(): DialogPanel = panel {
    useNewComboBoxRenderer()

    val languagesExtensions = LangManager.getInstance().supportedLanguagesExtensions.sortedBy { it.language }
    if (languagesExtensions.isNotEmpty()) {
      row(LearnBundle.message("learn.option.main.language")) {
        val options = languagesExtensions.mapNotNull { Language.findLanguageByID(it.language) }
          .map { LanguageOption(it) }
        comboBox(options)
          .bindItem({
                      val languageId = LangManager.getInstance().getLanguageId()
                      options.find { it.id == languageId } ?: options[0]
                    }, { language -> language?.let { resetPrimaryLanguage(it.id) } })
      }
    }
    row {
      button(LearnBundle.message("learn.option.reset.progress"), getActionById("ResetLearningProgressAction"), "settings")
    }
    row {
      checkBox(LearnBundle.message("settings.checkbox.show.notifications.new.lessons"))
        .bindSelected({ PropertiesComponent.getInstance().getBoolean(SHOW_NEW_LESSONS_NOTIFICATION, true) },
                      {
                        StatisticBase.logShowNewLessonsNotificationState(-1, CourseManager.instance.previousOpenedVersion, it)
                        PropertiesComponent.getInstance().setValue(SHOW_NEW_LESSONS_NOTIFICATION, it, true)
                      })
    }
  }

  private data class LanguageOption(val language: Language) {
    override fun toString(): String = language.displayName
    val id get() = language.id
  }
}
