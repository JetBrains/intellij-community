// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard.language

import com.intellij.ide.projectWizard.NewProjectWizardConstants.Generators.SIMPLE_MODULE
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Generators.SIMPLE_PROJECT
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.*
import com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter.Companion.NPW_PREFIX
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ui.UIBundle
import javax.swing.Icon

class BaseLanguageGeneratorNewProjectWizard(
  private val context: WizardContext,
  private val wizard: LanguageGeneratorNewProjectWizard
) : GeneratorNewProjectWizard {

  override val id: String = getLanguageGeneratorId(context, wizard.name)

  override val name: String = wizard.name

  override val icon: Icon = wizard.icon

  override val ordinal: Int = wizard.ordinal

  override val description: String =
    UIBundle.message("label.project.wizard.project.generator.description", context.isCreatingNewProjectInt, wizard.name)

  override fun isEnabled(): Boolean = wizard.isEnabled(context)

  override fun createStep(context: WizardContext): NewProjectWizardStep =
    RootNewProjectWizardStep(context)
      .nextStep(::newProjectWizardBaseStepWithoutGap)
      .nextStep(::GitNewProjectWizardStep)
      .nextStep { @Suppress("DEPRECATION") NewProjectWizardLanguageStep(it, wizard.name) }
      .nextStep(wizard::createStep)

  companion object {

    fun getLanguageGeneratorId(context: WizardContext, language: String): String {
      val generator = if (context.isCreatingNewProject) SIMPLE_PROJECT else SIMPLE_MODULE
      return "$generator.$language"
    }

    fun getLanguageModelBuilderId(context: WizardContext, language: String): String {
      return NPW_PREFIX + getLanguageGeneratorId(context, language)
    }
  }
}