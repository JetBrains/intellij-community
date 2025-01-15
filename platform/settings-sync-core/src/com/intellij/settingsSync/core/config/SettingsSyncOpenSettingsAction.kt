package com.intellij.settingsSync.core.config

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction

internal open class SettingsSyncOpenSettingsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    ShowSettingsUtil.getInstance().showSettingsDialog(e.project, SettingsSyncConfigurable::class.java)
  }

  class Simple : SettingsSyncOpenSettingsAction()
}