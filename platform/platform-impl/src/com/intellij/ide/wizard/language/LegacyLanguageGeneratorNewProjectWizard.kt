// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.ide.wizard.language

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.*
import com.intellij.ide.wizard.LanguageNewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.language.BaseLanguageGeneratorNewProjectWizard.Companion.getLanguageGeneratorId
import com.intellij.ui.UIBundle
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
class LegacyLanguageGeneratorNewProjectWizard(
  private val context: WizardContext,
  private val wizard: LanguageNewProjectWizard
) : GeneratorNewProjectWizard {

  override val id: String = getLanguageGeneratorId(context, wizard.name)

  override val name: String = wizard.name

  override val icon: Icon = EmptyIcon.ICON_16

  override val ordinal: Int = wizard.ordinal

  override val description: String =
    UIBundle.message("label.project.wizard.project.generator.description", context.isCreatingNewProjectInt, wizard.name)

  override fun isEnabled(): Boolean = wizard.isEnabled(context)

  override fun createStep(context: WizardContext): NewProjectWizardStep =
    RootNewProjectWizardStep(context)
      .nextStep(::newProjectWizardBaseStepWithoutGap)
      .nextStep(::GitNewProjectWizardStep)
      .nextStep { NewProjectWizardLanguageStep(it, wizard.name) }
      .nextStep(wizard::createStep)
}