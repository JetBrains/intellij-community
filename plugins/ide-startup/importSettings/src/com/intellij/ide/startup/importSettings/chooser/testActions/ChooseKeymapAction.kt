// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.testActions

import com.intellij.ide.startup.importSettings.chooser.ui.OnboardingController
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class ChooseKeymapAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    OnboardingController.getInstance().startWizard(isModal = false)
  }
}