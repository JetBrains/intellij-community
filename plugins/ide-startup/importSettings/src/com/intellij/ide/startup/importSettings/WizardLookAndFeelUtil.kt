// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.LafManagerImpl
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ComponentUtil
import javax.swing.JComponent
import javax.swing.SwingUtilities

object WizardLookAndFeelUtil {
  fun applyLookAndFeelToWizardWindow(laf: UIThemeLookAndFeelInfo, component: JComponent) {
    (LafManager.getInstance() as? LafManagerImpl)?.apply {
      updateLafNoSave(laf)
      updateUI()
      repaintUI()
    } ?: run {
      logger.error("The current LaF manager is not a LafManagerImpl.")
    }

    val dialogWindow = DialogWrapper.findInstance(component)
    if (dialogWindow != null) {
      SwingUtilities.updateComponentTreeUI(dialogWindow.window)
      ComponentUtil.decorateWindowHeader(dialogWindow.rootPane)
    }
  }
}

private val logger = logger<WizardLookAndFeelUtil>()
