// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.project.Project
import com.intellij.ui.layout.*
import com.intellij.util.PlatformUtils

class HTMLNewProjectWizard : NewProjectWizard {
  override val name: String = "HTML"

  override val isEnabled = PlatformUtils.isCommunityEdition()

  override fun createStep(parent: NewProjectStep.Step) = Step(parent)

  class Step(parent: NewProjectStep.Step) : AbstractNewProjectWizardChildStep<NewProjectStep.Step>(parent) {
    override fun setupUI(builder: LayoutBuilder) {}

    override fun setupProject(project: Project) {
      TODO("Not yet implemented")
    }
  }
}