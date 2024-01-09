// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.wizard.pluginChooser

import com.intellij.ide.startup.importSettings.chooser.ui.OnboardingPage
import com.intellij.ide.startup.importSettings.chooser.ui.WizardController
import com.intellij.platform.ide.bootstrap.StartupWizardStage
import java.awt.Component
import javax.swing.JComponent

class PluginChooserPage(val controller: WizardController) : OnboardingPage {
  override val content: JComponent
    get() = TODO("Not yet implemented")
  override val stage: StartupWizardStage = StartupWizardStage.WizardPluginPage

  override fun confirmExit(parentComponent: Component?): Boolean  = true
}