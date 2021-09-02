// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewModuleStep.Step
import com.intellij.ui.UIBundle

class NewProjectStep(context: WizardContext) : NewModuleStep(context) {

  override val steps: List<NewProjectWizardStep> = Step(context).let { listOf(it, Step(it)) }

  class Step(
    parent: NewModuleStep.Step
  ) : AbstractNewProjectWizardMultiStep<NewModuleStep.Step, Step>(parent, NewProjectWizard.EP_NAME),
      NewProjectWizardLanguageData,
      NewProjectWizardData by parent {

    override val self = this

    override val label = UIBundle.message("label.project.wizard.new.project.language")

    override val languageProperty by ::stepProperty
    override val language by ::step
  }
}
