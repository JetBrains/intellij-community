// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.util.Key
import com.intellij.ui.UIBundle

class NewProjectStep(context: WizardContext) : NewModuleStep(BASE_STEP_KEY, context) {

  override val steps = super.steps + Step(context)

  class Step(context: WizardContext) : NewProjectWizardMultiStep(context, NewProjectWizard.EP_NAME) {
    override val label = UIBundle.message("label.project.wizard.new.project.language")

    init {
      LANGUAGE_STEP_KEY.set(context, this)
    }
  }

  @Suppress("unused")
  companion object {
    val BASE_STEP_KEY = Key.create<NewModuleStep.Step>(NewProjectStep::class.java.name + "#" + NewModuleStep.Step::class.java.name)
    fun getNameProperty(context: WizardContext) = BASE_STEP_KEY.get(context).nameProperty
    fun getPathProperty(context: WizardContext) = BASE_STEP_KEY.get(context).pathProperty
    fun getGitProperty(context: WizardContext) = BASE_STEP_KEY.get(context).gitProperty
    fun getName(context: WizardContext) = BASE_STEP_KEY.get(context).name
    fun getPath(context: WizardContext) = BASE_STEP_KEY.get(context).projectPath
    fun getGit(context: WizardContext) = BASE_STEP_KEY.get(context).git

    val LANGUAGE_STEP_KEY = Key.create<Step>(Step::class.java.name)
    fun getLanguageProperty(context: WizardContext) = LANGUAGE_STEP_KEY.get(context).stepProperty
    fun getLanguage(context: WizardContext) = LANGUAGE_STEP_KEY.get(context).step
  }
}
