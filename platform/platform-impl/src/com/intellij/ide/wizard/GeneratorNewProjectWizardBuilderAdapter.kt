// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.application.Experiments
import javax.swing.Icon

/**
 * A base adapter class to turn a [GeneratorNewProjectWizard] into a
 * [com.intellij.ide.util.projectWizard.ModuleBuilder] and register as an extension point.
 */
abstract class GeneratorNewProjectWizardBuilderAdapter(val wizard: GeneratorNewProjectWizard) : AbstractNewProjectWizardBuilder() {
  override fun getPresentableName(): String = wizard.name
  override fun getDescription(): String = wizard.description ?: ""
  override fun getGroupName(): String = wizard.groupName ?: super.getGroupName()
  override fun getNodeIcon(): Icon = wizard.icon
  override fun createStep(context: WizardContext): NewProjectWizardStep = wizard.createStep(context)
  override fun isAvailable(): Boolean = Experiments.getInstance().isFeatureEnabled("new.project.wizard")
}