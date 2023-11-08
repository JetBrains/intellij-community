// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.productChooser

import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.StartupImportIcons
import com.intellij.ide.startup.importSettings.chooser.settingChooser.createDialog
import com.intellij.ide.startup.importSettings.chooser.ui.PageProvider
import com.intellij.ide.startup.importSettings.data.JBrActionsDataProvider
import com.intellij.ide.startup.importSettings.data.SettingsService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.DumbAwareAction

class ConfigAction(val callback: (PageProvider) -> Unit) : DumbAwareAction() {
  val service = SettingsService.getInstance().getJbService()


  init {
    templatePresentation.text = ImportSettingsBundle.message("import.from.custom.dir")
    templatePresentation.icon = StartupImportIcons.Icons.ConfigFile
  }

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun update(e: AnActionEvent) {
  }

  override fun actionPerformed(e: AnActionEvent) {
    //TODO implement
    //callback(createDialog(JBrActionsDataProvider.getInstance(), config))
  }
}