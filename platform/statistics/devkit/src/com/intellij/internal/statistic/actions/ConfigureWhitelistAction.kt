// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.StatisticsDevKitUtil
import com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogWhitelistSettingsPersistence
import com.intellij.internal.statistic.eventLog.validator.persistence.WhitelistPathSettings
import com.intellij.internal.statistic.eventLog.validator.rules.impl.TestModeValidationRule
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.LayeredIcon
import com.intellij.ui.components.dialog

class ConfigureWhitelistAction(private var myRecorderId: String = StatisticsDevKitUtil.DEFAULT_RECORDER)
  : DumbAwareAction(ActionsBundle.message("action.ConfigureWhitelistAction.text"),
                    ActionsBundle.message("action.ConfigureWhitelistAction.description"),
                    null) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val whitelistConfigurationModel = WhitelistConfigurationModel()
    val dialog = dialog(
      title = "Configure Whitelist",
      panel = whitelistConfigurationModel.panel,
      resizable = true,
      project = project,
      ok = { listOfNotNull(whitelistConfigurationModel.validate()) }
    )

    if (!dialog.showAndGet()) return

    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Saving Whitelist Configuration...", false) {
      override fun run(indicator: ProgressIndicator) {
        updateWhitelistSettings(whitelistConfigurationModel.recorderToSettings)
      }
    })
  }

  private fun updateWhitelistSettings(recorderToSettings: MutableMap<String, WhitelistConfigurationModel.WhitelistPathSettings>) {
    val whitelistSettingsPersistence = EventLogWhitelistSettingsPersistence.getInstance()
    for ((recorder, settings) in recorderToSettings) {
      val customPath = settings.customPath
      if (settings.useCustomPath && customPath != null) {
        whitelistSettingsPersistence.setPathSettings(recorder, WhitelistPathSettings(customPath, true))
      }
      else {
        val oldSettings = whitelistSettingsPersistence.getPathSettings(recorder)
        if (oldSettings != null && oldSettings.isUseCustomPath) {
          whitelistSettingsPersistence.setPathSettings(recorder, WhitelistPathSettings(oldSettings.customPath, false))
        }
      }
      SensitiveDataValidator.getInstance(recorder).update()
    }
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    val presentation = event.presentation
    presentation.isEnabled = TestModeValidationRule.isTestModeEnabled()
    val settings = EventLogWhitelistSettingsPersistence.getInstance().getPathSettings(myRecorderId)
    presentation.icon = if (settings != null && settings.isUseCustomPath) customPathConfiguredIcon else AllIcons.General.Settings
  }

  companion object {
    private val customPathConfiguredIcon = LayeredIcon(AllIcons.General.Settings, AllIcons.Nodes.WarningMark)
  }
}

