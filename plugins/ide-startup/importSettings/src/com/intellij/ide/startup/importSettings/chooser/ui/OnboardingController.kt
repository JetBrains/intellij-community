// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.ide.startup.importSettings.data.WizardProvider
import com.intellij.openapi.util.NlsContexts

class OnboardingController private constructor(){
  companion object {
    private val controller = OnboardingController()
    fun getInstance(): OnboardingController = controller
  }

  private var state = State.NON
  enum class State {
    NON,
    IMPORT,
    WIZARD,
    CLOSED
  }

  private var dialog: OnboardingDialog? = null

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

    val dl = getDialog()

    val skipAction: () -> Unit = skipImportAction ?:
      WizardProvider.getInstance().getWizardService()?.let {
      { startWizard(cancelCallback, title, isModal) }
    } ?: run {
      { dialogClose() }
    }

    val controller = ImportSettingsController.createController(dl, skipAction)

    cancelImportCallback = cancelCallback
    controller.goToProductChooserPage()
    dl.isModal = isModal

    if(!dl.isShowing) {
      dl.initialize()
      dl.show()
    }

    dl.title = title
    state = State.IMPORT
  }

  fun dialogClose() {
    dialog?.dialogClose()
  }

  private fun getDialog(): OnboardingDialog {
    val dl = dialog?.let {
       if(!it.isShowing || !it.isVisible) {
          createDialog()
       } else it
    } ?: run {
      createDialog()
    }
    dialog = dl
    return dl
  }

  fun startWizard(cancelCallback: (() -> Unit)? = null,
                  @NlsContexts.DialogTitle title: String? = null,
                  isModal: Boolean = true,
                  goBackAction: (() -> Unit)? = {startImport (cancelCallback, title, isModal)}) {

    val dl = getDialog()

    val service = WizardProvider.getInstance().getWizardService() ?: return

    val wizardController = WizardController.createController(dl, service, goBackAction)
    cancelImportCallback = cancelCallback

    wizardController.goToThemePage()

    if(!dl.isShowing) {
      dl.initialize()
      dl.show()
    }

    dl.title = title
    state = State.WIZARD
  }

}