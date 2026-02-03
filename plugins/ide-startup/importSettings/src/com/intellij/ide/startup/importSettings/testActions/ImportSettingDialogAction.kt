// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.testActions

import com.intellij.ide.startup.importSettings.chooser.ui.OnboardingController
import com.intellij.ide.startup.importSettings.data.SettingsService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.bootstrap.StartupWizardStage
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ImportSettingDialogAction : DumbAwareAction() {
  companion object {
    val titleGetter: (StartupWizardStage?) -> @NlsContexts.DialogTitle String? = { stage ->
       stage?.name
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  override fun actionPerformed(e: AnActionEvent) {
    GlobalScope.launch { // global scope because it's an internal debugging action only
      SettingsService.getInstance().warmUp() // necessary to detect the products
    }
    OnboardingController.getInstance().startImport(isModal = false, titleGetter = titleGetter)
  }
}