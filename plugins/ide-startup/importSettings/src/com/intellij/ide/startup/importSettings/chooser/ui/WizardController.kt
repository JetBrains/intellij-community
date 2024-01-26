// Copyright 2000-2023 JetBrains s.r.o. and contr(ibutors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.ide.startup.importSettings.data.WizardService
import com.intellij.ide.startup.importSettings.wizard.keymapChooser.KeymapChooserPage
import com.intellij.ide.startup.importSettings.wizard.pluginChooser.WizardPluginsPage
import com.intellij.ide.startup.importSettings.wizard.pluginChooser.WizardProgressPage
import com.intellij.ide.startup.importSettings.wizard.themeChooser.ThemeChooserPage
import com.intellij.util.ui.accessibility.ScreenReader

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
  fun goToInstallPluginPage(ids: List<String>)
  fun skipPlugins()
}

class WizardControllerImpl(dialog: OnboardingDialog,
                           override val service: WizardService,
                           override val goBackAction: (() -> Unit)?) : WizardController, BaseControllerImpl(dialog) {

  override fun goToThemePage() {
    if (ScreenReader.isActive()) {
      goToKeymapPage()
      return
    }

    val page = ThemeChooserPage(this)
    dialog.changePage(page)
  }

  override fun goToKeymapPage() {
    val page = KeymapChooserPage(this)
    dialog.changePage(page)
  }

  override fun goToPluginPage() {
    val page = WizardPluginsPage(this)
    dialog.changePage(page)
  }

  override fun goToInstallPluginPage(ids: List<String>) {
    if (ids.isNotEmpty()) {
      val importProgress = service.getPluginService().install(ids)
      val page = WizardProgressPage(importProgress, this)
      dialog.changePage(page)
    }
    else {
      skipPlugins()
    }
  }

  override fun skipPlugins() {
    service.getPluginService().skipPlugins()
    dialog.dialogClose()
  }
}
