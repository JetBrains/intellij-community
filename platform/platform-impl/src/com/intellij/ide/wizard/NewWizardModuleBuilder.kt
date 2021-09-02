// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

abstract class NewWizardModuleBuilder : ModuleBuilder() {
  private var step: NewModuleStep? = null

  abstract fun createStep(context: WizardContext): NewModuleStep

  final override fun getCustomOptionsStep(context: WizardContext, parentDisposable: Disposable): ModuleWizardStep {
    return createStep(context).also { step = it }
  }

  override fun createProject(name: String?, path: String?): Project? {
    return super.createProject(name, path)
      ?.also { step!!.setupProject(it) }
  }

  override fun cleanup() {
    step = null
  }

  companion object {
    const val DEFAULT_GROUP = "Default"
    const val GENERATORS = "Generators"
  }
}