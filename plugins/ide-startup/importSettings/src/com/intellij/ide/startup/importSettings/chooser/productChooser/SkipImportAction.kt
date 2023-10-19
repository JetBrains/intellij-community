// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.productChooser

import com.intellij.ide.ui.laf.darcula.ui.OnboardingDialogButtons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import javax.swing.JButton

class SkipImportAction(val doClose: () -> Unit) : ChooseProductActionButton("Skip Import") {
  init {
    templatePresentation.text = "Skip Import"
    templatePresentation.icon = null
  }

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun actionPerformed(e: AnActionEvent) {
    doClose()
  }


  override fun createButton(presentation: Presentation): JButton {
    return OnboardingDialogButtons.createHoveredLinkButton()
  }

}
