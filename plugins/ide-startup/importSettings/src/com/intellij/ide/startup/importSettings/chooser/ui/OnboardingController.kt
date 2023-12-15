// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

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
      //this.isModal = bla
    }
  }

  private fun doCancelAction() {
    state = State.CLOSED
    cancelImportCallback?.let { it() }
  }

  private var cancelImportCallback: (() -> Unit)? = null

  fun startImport(cancelCallback: (() -> Unit)? = null,
                  @NlsContexts.DialogTitle title: String? = null, isModal: Boolean = true) {

    if(!dialog.isShowing || !dialog.isVisible) {
      dialog = createDialog()
    }

    val controller = ImportSettingsController.createController(dialog)

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

  fun startWizard(cancelCallback: (() -> Unit)? = null,
                  @NlsContexts.DialogTitle title: String? = null) {

    if(!dialog.isShowing || !dialog.isVisible) {
      dialog = createDialog()
    }

    cancelImportCallback = cancelCallback

    if(!dialog.isShowing) {
      dialog.initialize()
      dialog.show()
    }

    dialog.title = title
    state = State.IMPORT
  }

}