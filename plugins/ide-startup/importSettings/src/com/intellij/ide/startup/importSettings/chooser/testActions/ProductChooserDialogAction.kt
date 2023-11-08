// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.testActions

import com.intellij.ide.startup.importSettings.chooser.productChooser.ProductChooserDialog
import com.intellij.ide.startup.importSettings.chooser.ui.MultiplePageDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class ProductChooserDialogAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    MultiplePageDialog.show(ProductChooserDialog(), isModal = false)
  }
}

class ProductChooserSingleDialogAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val dialog = ProductChooserDialog()
    dialog.isModal = false
    dialog.isResizable = false
    dialog.show()

    dialog.pack()
  }
}