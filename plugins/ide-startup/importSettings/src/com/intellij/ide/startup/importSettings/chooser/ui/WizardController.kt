// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.ide.startup.importSettings.data.StartupWizardService
import com.intellij.ide.startup.importSettings.wizard.keymapChooser.KeymapChooserPage
import com.intellij.ide.startup.importSettings.wizard.pluginChooser.WizardPluginsPage
import com.intellij.ide.startup.importSettings.wizard.pluginChooser.WizardProgressPage
import com.intellij.ide.startup.importSettings.wizard.themeChooser.ThemeChooserPage
import com.intellij.util.ui.accessibility.ScreenReader
import com.jetbrains.rd.util.lifetime.SequentialLifetimes

interface WizardController : BaseController {
  companion object {
    fun createController(dialog: OnboardingDialog, service: StartupWizardService, goBackAction: (() -> Unit)?): WizardController {
      return WizardControllerImpl(dialog, service, goBackAction)
    }
  }

  val goBackAction: (() -> Unit)?

  val service: StartupWizardService

  fun goToThemePage()
  fun goToKeymapPage()
  fun goToPluginPage()
  fun goToInstallPluginPage(ids: List<String>)
  fun skipPlugins()

  fun cancelPluginInstallation()
}

class WizardControllerImpl(dialog: OnboardingDialog,
                           override val service: StartupWizardService,
                           override val goBackAction: (() -> Unit)?) : WizardController, BaseControllerImpl(dialog) {

  private val installationLifetimes = SequentialLifetimes(lifetime)

  init {
    service.shouldClose.advise(lifetime) {
      dialog.dialogClose()
    }
  }

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
      val lifetime = installationLifetimes.next()
      val importProgress = service.getPluginService().install(lifetime, ids)
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

  override fun cancelPluginInstallation() {
    installationLifetimes.terminateCurrent()
  }
}
