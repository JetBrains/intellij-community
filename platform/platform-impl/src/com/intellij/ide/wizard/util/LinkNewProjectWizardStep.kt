// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard.util

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.Step
import javax.swing.event.HyperlinkEvent

abstract class LinkNewProjectWizardStep(parent: NewProjectWizardStep) : CommentNewProjectWizardStep(parent) {

  abstract val builderId: String

  open fun onStepSelected(step: Step) {}

  open fun onStepSelected(step: NewProjectWizardStep) {}

  override fun onHyperlinkActivated(e: HyperlinkEvent) {
    context.requestSwitchTo(builderId) { step ->
      onStepSelected(step)
      if (step is NewProjectWizardStep) {
        onStepSelected(step as NewProjectWizardStep)
      }
    }
  }
}