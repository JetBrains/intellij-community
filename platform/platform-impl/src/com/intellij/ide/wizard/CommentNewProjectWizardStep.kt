// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.Label
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel

abstract class CommentNewProjectWizardStep(parent: NewProjectWizardStep, var text: @Label String) : AbstractNewProjectWizardStep(parent) {
  override fun setupUI(builder: Panel) {
    builder.row {
      label(text)
    }.bottomGap(BottomGap.SMALL)
  }
  override fun setupProject(project: Project) {}
}