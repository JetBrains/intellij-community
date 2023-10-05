// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.productChooser

import com.intellij.ide.startup.importSettings.chooser.settingChooser.SettingChooserDialog
import com.intellij.ide.startup.importSettings.chooser.ui.PageProvider
import com.intellij.ide.startup.importSettings.data.JBrActionsDataProvider
import com.intellij.ide.startup.importSettings.data.SettingsService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class ConfigAction(val callback: (PageProvider) -> Unit) : DumbAwareAction() {
  val service = SettingsService.getInstance().getJbService()
  val config
    get() = service.getConfig()


  init {
    templatePresentation.text = config.name
    templatePresentation.icon = service.getProductIcon(config.id)
  }

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun update(e: AnActionEvent) {
    e.presentation.text = config.name
    e.presentation.icon = service.getProductIcon(config.id)
  }

  override fun actionPerformed(e: AnActionEvent) {
    callback(SettingChooserDialog(JBrActionsDataProvider.getInstance(), config))
  }
}