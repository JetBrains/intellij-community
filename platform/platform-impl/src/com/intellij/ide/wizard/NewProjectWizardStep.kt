// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.project.Project
import com.intellij.ui.layout.*

interface NewProjectWizardStep<S : NewProjectWizardStepSettings<S>> {
  val settings: S

  fun setupUI(builder: RowBuilder)

  fun setupProject(project: Project)

  interface Factory {
    fun createStep(context: WizardContext): NewProjectWizardStep<*>
  }
}