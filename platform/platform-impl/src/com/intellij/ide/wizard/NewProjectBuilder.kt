// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard

import com.intellij.ide.projectWizard.NewProjectWizardConstants.Generators
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ui.UIBundle
import com.intellij.util.ui.EmptyIcon
import javax.swing.Icon

class NewProjectBuilder : GeneratorNewProjectWizardBuilderAdapter(SimpleNewProjectWizard()) {
  class SimpleNewProjectWizard : GeneratorNewProjectWizard {
    override val id: String = Generators.SIMPLE_PROJECT
    override val name: String = UIBundle.message("label.project.wizard.project.generator.name")
    override val description: String = UIBundle.message("label.project.wizard.project.generator.description")
    override val icon: Icon = EmptyIcon.ICON_0

    override fun createStep(context: WizardContext) =
      RootNewProjectWizardStep(context).chain(
        ::newProjectWizardBaseStepWithoutGap,
        ::GitNewProjectWizardStep,
        ::NewProjectWizardLanguageStep
      )
  }
}
