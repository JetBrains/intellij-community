// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.settingChooser

import com.intellij.ide.startup.importSettings.chooser.ui.ImportSettingsController
import com.intellij.ide.startup.importSettings.chooser.ui.UiUtils
import com.intellij.ide.startup.importSettings.data.ActionsDataProvider
import com.intellij.ide.startup.importSettings.data.Product
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class SettingChooserItemAction(val product: Product, val provider: ActionsDataProvider<*>, private val controller: ImportSettingsController) : DumbAwareAction() {

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = true
    e.presentation.text = provider.getText(product)
    e.presentation.icon = provider.getProductIcon(product.id)
    provider.getComment(product)?.let {
      e.presentation.putClientProperty(UiUtils.DESCRIPTION, it)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    controller.goToSettingsPage(provider, product)
  }

}