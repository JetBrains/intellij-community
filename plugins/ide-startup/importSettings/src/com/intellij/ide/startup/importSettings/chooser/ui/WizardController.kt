// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.ide.startup.importSettings.data.WizardService
import com.intellij.ide.startup.importSettings.wizard.keymapChooser.KeymapChooserPage
import com.intellij.ide.startup.importSettings.wizard.pluginChooser.PluginChooserPage

interface WizardController : BaseController {
  companion object {
    fun createController(dialog: OnboardingDialog, service: WizardService, goBackAction: (() -> Unit)?): WizardController {
      return WizardControllerImpl(dialog, service, goBackAction)
    }
  }

  val goBackAction: (() -> Unit)?

  val service: WizardService

  fun goToThemePage()
  fun goToKeymapPage()
  fun goToPluginPage()

}

class WizardControllerImpl(dialog: OnboardingDialog,
                           override val service: WizardService, override val goBackAction: (() -> Unit)?) : WizardController, BaseControllerImpl(dialog){

  override fun goToThemePage() {
    TODO("Not yet implemented")
  }

  override fun goToKeymapPage() {
    val page = KeymapChooserPage(this)
    dialog.changePage(page)
  }

  override fun goToPluginPage() {
    val page = PluginChooserPage(this)
    dialog.changePage(page)
  }

}
