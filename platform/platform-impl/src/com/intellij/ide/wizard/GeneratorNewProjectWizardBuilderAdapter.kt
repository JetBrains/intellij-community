// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.module.ModuleType
import javax.swing.Icon

/**
 * A base adapter class to turn a [GeneratorNewProjectWizard] into a
 * [com.intellij.ide.util.projectWizard.ModuleBuilder] and register as an extension point.
 */
open class GeneratorNewProjectWizardBuilderAdapter(val wizard: GeneratorNewProjectWizard) : AbstractNewProjectWizardBuilder() {
  override fun getBuilderId(): String = wizard.id
  override fun getModuleType(): ModuleType<*>? = null
  override fun getPresentableName(): String = wizard.name
  override fun getDescription(): String? = wizard.description
  override fun getGroupName(): String = wizard.groupName ?: super.getGroupName()
  override fun getNodeIcon(): Icon = wizard.icon
  override fun createStep(context: WizardContext): NewProjectWizardStep = wizard.createStep(context)

  override fun getIgnoredSteps(): List<Class<out ModuleWizardStep>> {
    try {
      @Suppress("UNCHECKED_CAST")
      return listOf(Class.forName("com.intellij.ide.projectWizard.ProjectSettingsStep") as Class<ModuleWizardStep>)
    }
    catch (e: ClassNotFoundException) {
    }
    return emptyList()
  }

  override fun isAvailable(): Boolean = Experiments.getInstance().isFeatureEnabled("new.project.wizard")
}