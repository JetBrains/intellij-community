// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ui.UIBundle
import com.intellij.util.ui.EmptyIcon
import javax.swing.Icon

class NewModuleBuilder : AbstractNewProjectWizardBuilder() {
  override fun getPresentableName() = UIBundle.message("label.project.wizard.module.generator.name")
  override fun getDescription() = UIBundle.message("label.project.wizard.module.generator.description")
  override fun getNodeIcon(): Icon = EmptyIcon.ICON_0

  override fun createStep(context: WizardContext) =
    RootNewProjectWizardStep(context)
      .chain(::NewProjectWizardBaseStep, ::NewProjectWizardLanguageStep)
}
