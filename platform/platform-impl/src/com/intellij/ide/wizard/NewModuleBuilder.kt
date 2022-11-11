// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.projectWizard.NewProjectWizardConstants.Generators
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ui.UIBundle
import com.intellij.util.ui.EmptyIcon
import javax.swing.Icon

class NewModuleBuilder : GeneratorNewProjectWizardBuilderAdapter(SimpleNewModuleWizard()) {
  class SimpleNewModuleWizard : GeneratorNewProjectWizard {
    override val id: String = Generators.SIMPLE_MODULE
    override val name: String = UIBundle.message("label.project.wizard.module.generator.name")
    override val description: String = UIBundle.message("label.project.wizard.module.generator.description")
    override val icon: Icon = EmptyIcon.ICON_0

    override fun createStep(context: WizardContext) =
      RootNewProjectWizardStep(context)
        .chain(::NewProjectWizardBaseStep, ::NewProjectWizardLanguageStep)
  }
}
