// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.ide.startup.importSettings.statistics.ImportSettingsEventsCollector
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.bootstrap.StartupWizardStage

class OnboardingController private constructor(){
  companion object {
    private val controller = OnboardingController()
    fun getInstance(): OnboardingController = controller
  }

  private var state = State.NON
  enum class State {
    NON,
    IMPORT,
    CLOSED
  }

  private var dialog: OnboardingDialog? = null

  private fun createDialog(titleGetter: (StartupWizardStage?) -> @NlsContexts.DialogTitle String?): OnboardingDialog {
    return OnboardingDialog(titleGetter) { doCancelAction() }.apply {
      this.isResizable = false
    }
  }

  private fun doCancelAction() {
    state = State.CLOSED
    cancelImportCallback?.let { it() }
  }

  private var cancelImportCallback: (() -> Unit)? = null

  fun startImport(cancelCallback: (() -> Unit)? = null,
                  titleGetter: (StartupWizardStage?) -> @NlsContexts.DialogTitle String? = { null },
                  isModal: Boolean = true,
                  skipImportAction: (() -> Unit)? = null) {

    val dl = getDialog(titleGetter)

    val skipAction: () -> Unit = skipImportAction ?: {
      ImportSettingsEventsCollector.productPageSkipButton()
      dialogClose()
    }

    val controller = ImportSettingsController.createController(dl, skipAction)

    cancelImportCallback = cancelCallback
    controller.goToProductChooserPage()
    dl.isModal = isModal

    if (!dl.isShowing) {
      dl.initialize()
      dl.show()
      ImportSettingsEventsCollector.importFinished()
    }

    state = State.IMPORT
  }

  fun dialogClose() {
    dialog?.dialogClose()
  }

  private fun getDialog(titleGetter: (StartupWizardStage?) -> @NlsContexts.DialogTitle String?): OnboardingDialog {
    val dl = dialog?.let {
       if(!it.isShowing || !it.isVisible) {
          createDialog(titleGetter)
       } else {
         it.titleGetter = titleGetter
         it
       }
    } ?: run {
      createDialog(titleGetter)
    }
    dialog = dl
    return dl
  }

}
