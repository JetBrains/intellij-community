// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.ide.startup.importSettings.data.WizardProvider
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts

class OnboardingController private constructor(){
  companion object {
    val controller = OnboardingController()
    fun getInstance(): OnboardingController = controller
  }

  private var state = State.NON
  enum class State {
    NON,
    IMPORT,
    WIZARD,
    CLOSED
  }

  private var dialog = createDialog()

  private fun createDialog(): OnboardingDialog {
    return OnboardingDialog { doCancelAction() }.apply {
      this.isResizable = false
    }
  }

  private fun doCancelAction() {
    state = State.CLOSED
    cancelImportCallback?.let { it() }
  }

  private var cancelImportCallback: (() -> Unit)? = null

  fun startImport(cancelCallback: (() -> Unit)? = null,
                  @NlsContexts.DialogTitle title: String? = null,
                  isModal: Boolean = true,
                  skipImportAction: (() -> Unit)? = null) {

    if(!dialog.isShowing || !dialog.isVisible) {
      dialog = createDialog()
    }

    val skipAction: () -> Unit = skipImportAction ?:
      WizardProvider.getInstance().getWizardService()?.let {
      { startWizard(cancelCallback, title, isModal) }
    } ?: run {
      { dialogClose() }
    }

    val controller = ImportSettingsController.createController(dialog, skipAction)

    cancelImportCallback = cancelCallback
    controller.goToProductChooserPage()
    dialog.isModal = isModal

    if(!dialog.isShowing) {
      dialog.initialize()
      dialog.show()
    }

    dialog.title = title
    state = State.IMPORT
  }

  fun dialogClose() {
    if(dialog.isShowing && dialog.isVisible) {
      dialog.doClose(DialogWrapper.CANCEL_EXIT_CODE)
    }
  }


  fun startWizard(cancelCallback: (() -> Unit)? = null,
                  @NlsContexts.DialogTitle title: String? = null,
                  isModal: Boolean = true,
                  goBackAction: (() -> Unit)? = {startImport (cancelCallback, title, isModal)}) {

    if(!dialog.isShowing || !dialog.isVisible) {
      dialog = createDialog()
    }

    val service = WizardProvider.getInstance().getWizardService() ?: return

    val wizardController = WizardController.createController(dialog, service, goBackAction)
    cancelImportCallback = cancelCallback

    wizardController.goToKeymapPage()

    if(!dialog.isShowing) {
      dialog.initialize()
      dialog.show()
    }

    dialog.title = title
    state = State.IMPORT
  }

}