// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.testActions

import com.intellij.ide.startup.importSettings.chooser.ui.OnboardingController
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.bootstrap.StartupWizardStage

class ImportSettingDialogAction : DumbAwareAction() {
  companion object {
    val titleGetter: (StartupWizardStage?) -> @NlsContexts.DialogTitle String? = { stage ->
       stage?.name
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    OnboardingController.getInstance().startImport(isModal = false, titleGetter = titleGetter)
  }
}