// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.testActions

import com.intellij.ide.startup.importSettings.chooser.importProgress.ImportProgressDialog
import com.intellij.ide.startup.importSettings.chooser.ui.MultiplePageDialog
import com.intellij.ide.startup.importSettings.data.TestJbService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class ImportProgressDialogAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    MultiplePageDialog.show(ImportProgressDialog(TestJbService.simpleImport), isModal = false)
  }
}

class ImportProgressSingleDialogAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val dialog = ImportProgressDialog(TestJbService.importFromProduct)
    dialog.isModal = false
    dialog.isResizable = false
    dialog.show()

    dialog.pack()
  }
}

class ImportProgressSimpleDialogAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val dialog = ImportProgressDialog(TestJbService.simpleImport)
    dialog.isModal = false
    dialog.isResizable = false
    dialog.show()

    dialog.pack()
  }
}