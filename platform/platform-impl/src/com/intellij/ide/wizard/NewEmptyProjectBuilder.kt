// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.GeneralModuleType
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.project.Project
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.ui.EmptyIcon
import javax.swing.Icon

class NewEmptyProjectBuilder : AbstractNewProjectWizardBuilder() {
  override fun getPresentableName() = UIBundle.message("label.project.wizard.empty.project.generator.name")
  override fun getDescription() = UIBundle.message("label.project.wizard.empty.project.generator.description")
  override fun getNodeIcon(): Icon = EmptyIcon.ICON_0

  override fun createStep(context: WizardContext) =
    RootNewProjectWizardStep(context).chain(::DescriptionStep, ::NewProjectWizardBaseStep, ::GitNewProjectWizardStep, ::Step)

  private class DescriptionStep(parent: NewProjectWizardStep) :
    CommentNewProjectWizardStep(parent,  UIBundle.message("label.project.wizard.empty.project.generator.full.description"))

  private class Step(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {
    override fun setupUI(builder: Panel) {}

    override fun setupProject(project: Project) {
      val moduleType = ModuleTypeManager.getInstance().findByID(GeneralModuleType.TYPE_ID)
      moduleType.createModuleBuilder().commit(project)
    }
  }
}