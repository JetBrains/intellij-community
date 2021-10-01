// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.project.Project

abstract class AbstractNewProjectWizardBuilder(private val factory: NewProjectWizardStep.RootStepFactory) : ModuleBuilder() {
  private var step: NewModuleStep? = null

  final override fun getCustomOptionsStep(context: WizardContext, parentDisposable: Disposable): ModuleWizardStep {
    return NewModuleStep(context, factory)
      .also { step = it }
  }

  override fun commitModule(project: Project, model: ModifiableModuleModel?): Nothing? {
    step!!.setupProject(project)
    return null
  }

  override fun cleanup() {
    step = null
  }

  companion object {
    const val DEFAULT_GROUP = "Default"
    const val GENERATORS = "Generators"
  }
}