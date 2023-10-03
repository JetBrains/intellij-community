// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.settingChooser

import com.intellij.ide.startup.importSettings.data.ActionsDataProvider
import com.intellij.ide.startup.importSettings.data.Product
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper

class SettingChooserItemAction(val product: Product, val provider: ActionsDataProvider<*>, val callback: (Int) -> Unit) : DumbAwareAction() {

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = true
    e.presentation.text = provider.getText(product)
    e.presentation.icon = provider.getProductIcon(product.id)
  }

  override fun actionPerformed(e: AnActionEvent) {
    callback(DialogWrapper.OK_EXIT_CODE)

    val dialog = createDialog(provider, product)
    dialog.isModal = false
    dialog.isResizable = false
    dialog.show()

    dialog.pack()

  }

}