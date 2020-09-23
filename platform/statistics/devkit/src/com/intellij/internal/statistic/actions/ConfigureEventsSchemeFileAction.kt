// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.StatisticsDevKitUtil
import com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataSettingsPersistence
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventsSchemePathSettings
import com.intellij.internal.statistic.eventLog.validator.rules.impl.TestModeValidationRule
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.LayeredIcon
import com.intellij.ui.components.dialog

class ConfigureEventsSchemeFileAction(private var myRecorderId: String = StatisticsDevKitUtil.DEFAULT_RECORDER)
  : DumbAwareAction(ActionsBundle.message("action.ConfigureEventsSchemeFileAction.text"),
                    ActionsBundle.message("action.ConfigureEventsSchemeFileAction.description"),
                    null) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val configurationModel = EventsSchemeConfigurationModel()
    val dialog = dialog(
      title = "Configure Custom Events Scheme",
      panel = configurationModel.panel,
      resizable = true,
      project = project,
      ok = { listOfNotNull(configurationModel.validate()) }
    )

    if (!dialog.showAndGet()) return

    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Saving Events Scheme Configuration...", false) {
      override fun run(indicator: ProgressIndicator) {
        updateSchemeSettings(configurationModel.recorderToSettings)
      }
    })
  }

  private fun updateSchemeSettings(recorderToSettings: MutableMap<String, EventsSchemeConfigurationModel.EventsSchemePathSettings>) {
    val settingsPersistence = EventLogMetadataSettingsPersistence.getInstance()
    for ((recorder, settings) in recorderToSettings) {
      val customPath = settings.customPath
      if (settings.useCustomPath && customPath != null) {
        settingsPersistence.setPathSettings(recorder, EventsSchemePathSettings(customPath, true))
      }
      else {
        val oldSettings = settingsPersistence.getPathSettings(recorder)
        if (oldSettings != null && oldSettings.isUseCustomPath) {
          settingsPersistence.setPathSettings(recorder, EventsSchemePathSettings(oldSettings.customPath, false))
        }
      }
      val validator = SensitiveDataValidator.getInstance(recorder)
      validator.update()
      validator.reload()
    }
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    val presentation = event.presentation
    presentation.isEnabled = TestModeValidationRule.isTestModeEnabled()
    val settings = EventLogMetadataSettingsPersistence.getInstance().getPathSettings(myRecorderId)
    presentation.icon = if (settings != null && settings.isUseCustomPath) customPathConfiguredIcon else AllIcons.General.Settings
  }

  companion object {
    private val customPathConfiguredIcon = LayeredIcon(AllIcons.General.Settings, AllIcons.Nodes.WarningMark)
  }
}

