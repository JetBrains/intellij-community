// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.projectWizard.NewProjectWizardCollector
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.nameProperty
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.pathProperty
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import javax.swing.Icon

abstract class AbstractNewProjectWizardBuilder : ModuleBuilder() {
  private var panel: NewProjectWizardStepPanel? = null

  abstract override fun getPresentableName(): String
  abstract override fun getDescription(): String
  abstract override fun getNodeIcon(): Icon

  protected abstract fun createStep(context: WizardContext): NewProjectWizardStep

  final override fun getModuleType() =
    object : ModuleType<AbstractNewProjectWizardBuilder>("newWizard.${this::class.java.name}") {
      override fun createModuleBuilder() = this@AbstractNewProjectWizardBuilder
      override fun getName() = this@AbstractNewProjectWizardBuilder.presentableName
      override fun getDescription() = this@AbstractNewProjectWizardBuilder.description
      override fun getNodeIcon(isOpened: Boolean) = this@AbstractNewProjectWizardBuilder.nodeIcon
    }

  final override fun getCustomOptionsStep(context: WizardContext, parentDisposable: Disposable): ModuleWizardStep {
    val wizardStep = createStep(context)
    wizardStep.pathProperty.afterChange {
      NewProjectWizardCollector.logLocationChanged(context, this::class.java)
    }
    wizardStep.nameProperty.afterChange {
      NewProjectWizardCollector.logNameChanged(context, this::class.java)
    }

    panel = NewProjectWizardStepPanel(wizardStep)
    return BridgeStep(panel!!)
  }

  override fun commitModule(project: Project, model: ModifiableModuleModel?): Module? {
    val step = panel!!.step
    return detectCreatedModule(project) {
      step.setupProject(project)
      NewProjectWizardCollector.logGeneratorFinished(step.context, this::class.java)
    }
  }

  override fun cleanup() {
    panel = null
  }

  private class BridgeStep(private val panel: NewProjectWizardStepPanel) : ModuleWizardStep(),
                                                                           NewProjectWizardStep by panel.step {

    override fun validate() = panel.validate()

    override fun updateDataModel() = panel.apply()

    override fun getPreferredFocusedComponent() = panel.getPreferredFocusedComponent()

    override fun getComponent() = panel.component
  }

  companion object {
    private fun detectCreatedModule(project: Project, action: () -> Unit): Module? {
      val manager = ModuleManager.getInstance(project)
      val modules = manager.modules
      action()
      return manager.modules.find { it !in modules }
    }
  }
}