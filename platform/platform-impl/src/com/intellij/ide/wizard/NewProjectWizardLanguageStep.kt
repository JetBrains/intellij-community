// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.projectWizard.NewProjectWizardCollector.Companion.logLanguageChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Companion.logLanguageFinished
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.openapi.project.Project
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.util.PlatformUtils

class NewProjectWizardLanguageStep(parent: NewProjectWizardStep) :
  AbstractNewProjectWizardMultiStepWithAddButton<NewProjectWizardLanguageStep, LanguageNewProjectWizard>(parent, LanguageNewProjectWizard.EP_NAME),
  LanguageNewProjectWizardData,
  NewProjectWizardBaseData by parent.baseData {

  override val self = this

  override val label = UIBundle.message("label.project.wizard.new.project.language")

  override val languageProperty by ::stepProperty
  override var language by ::step

  override var additionalStepPlugins =
    if (PlatformUtils.isIdeaCommunity())
      mapOf(
        Language.PYTHON to "PythonCore",
        Language.SCALA to "org.intellij.scala"
      )
    else
      mapOf(
        Language.GO to "org.jetbrains.plugins.go",
        Language.RUBY to "org.jetbrains.plugins.ruby",
        Language.PHP to "com.jetbrains.php",
        Language.PYTHON to "Pythonid",
        Language.SCALA to "org.intellij.scala"
      )

  override fun createAndSetupSwitcher(builder: Row): SegmentedButton<String> {
    return super.createAndSetupSwitcher(builder)
      .whenItemSelectedFromUi { logLanguageChanged() }
  }

  init {
    data.putUserData(LanguageNewProjectWizardData.KEY, this)
  }

  override fun setupProject(project: Project) {
    super.setupProject(project)
    logLanguageFinished(context, step)
  }
}
