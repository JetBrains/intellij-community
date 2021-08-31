// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.util.Key
import com.intellij.ui.UIBundle

class NewProjectStep(context: WizardContext) : NewModuleStep(KEY, context) {

  override val steps = super.steps + Step(context)

  class Step(context: WizardContext) : NewProjectWizardMultiStep(context, NewProjectWizard.EP_NAME) {
    override val label = UIBundle.message("label.project.wizard.new.project.language")
  }

  companion object {
    val KEY = Key.create<NewModuleStep.Step>(NewModuleStep.Step::class.java.name)

    fun getNameProperty(context: WizardContext) = KEY.get(context).nameProperty
    fun getPathProperty(context: WizardContext) = KEY.get(context).pathProperty
    fun getName(context: WizardContext) = KEY.get(context).name
    fun getPath(context: WizardContext) = KEY.get(context).projectPath
  }
}
