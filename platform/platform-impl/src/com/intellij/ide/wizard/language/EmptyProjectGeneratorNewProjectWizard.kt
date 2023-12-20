// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard.language

import com.intellij.ide.projectWizard.NewProjectWizardConstants
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.*
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.comment.CommentNewProjectWizardStep
import com.intellij.openapi.module.GeneralModuleType
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.project.Project
import com.intellij.ui.UIBundle
import com.intellij.util.ui.EmptyIcon
import javax.swing.Icon

class EmptyProjectGeneratorNewProjectWizard : GeneratorNewProjectWizard {
  override val id: String = NewProjectWizardConstants.Generators.EMPTY_PROJECT
  override val name: String = UIBundle.message("label.project.wizard.empty.project.generator.name")
  override val description: String = UIBundle.message("label.project.wizard.empty.project.generator.description")
  override val icon: Icon = EmptyIcon.ICON_16

  override fun createStep(context: WizardContext): NewProjectWizardStep =
    RootNewProjectWizardStep(context)
      .nextStep(::CommentStep)
      .nextStep(::newProjectWizardBaseStepWithoutGap)
      .nextStep(::GitNewProjectWizardStep)
      .nextStep(::Step)

  private class CommentStep(parent: NewProjectWizardStep) : CommentNewProjectWizardStep(parent) {
    override val comment: String = UIBundle.message("label.project.wizard.empty.project.generator.full.description")
  }

  private class Step(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {
    override fun setupProject(project: Project) {
      val moduleType = ModuleTypeManager.getInstance().findByID(GeneralModuleType.TYPE_ID)
      val builder = moduleType.createModuleBuilder()
      setupProjectFromBuilder(project, builder)
    }
  }
}