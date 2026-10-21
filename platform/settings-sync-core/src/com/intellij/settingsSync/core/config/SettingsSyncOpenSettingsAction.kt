package com.intellij.settingsSync.core.config

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction

internal open class SettingsSyncOpenSettingsAction : DumbAwareAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isEnabledAndVisible = e.place != ActionPlaces.ACTION_SEARCH
  }

  override fun actionPerformed(e: AnActionEvent) {
    ShowSettingsUtil.getInstance().showSettingsDialog(e.project, SettingsSyncConfigurable::class.java)
  }

  class Simple : SettingsSyncOpenSettingsAction()
}