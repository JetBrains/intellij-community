// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ui.UIBundle

class NewProjectWizardLanguageStep(
  parent: NewProjectWizardBaseStep
) : AbstractNewProjectWizardMultiStep<NewProjectWizardBaseStep, NewProjectWizardLanguageStep>(parent, NewProjectWizard.EP_NAME),
    NewProjectWizardLanguageData,
    NewProjectWizardBaseData by parent {

  override val self = this

  override val label = UIBundle.message("label.project.wizard.new.project.language")

  override val languageProperty by ::stepProperty
  override val language by ::step

  class Factory : NewProjectWizardChildStep.Factory<NewProjectWizardBaseStep> {
    override fun createStep(parent: NewProjectWizardBaseStep) = NewProjectWizardLanguageStep(parent)
  }
}
