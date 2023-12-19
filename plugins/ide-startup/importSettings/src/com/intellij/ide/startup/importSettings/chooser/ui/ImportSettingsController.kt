// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.ide.startup.importSettings.chooser.importProgress.ImportProgressPage
import com.intellij.ide.startup.importSettings.chooser.productChooser.ProductChooserPage
import com.intellij.ide.startup.importSettings.chooser.settingChooser.SettingChooserPage
import com.intellij.ide.startup.importSettings.data.ActionsDataProvider
import com.intellij.ide.startup.importSettings.data.DialogImportData
import com.intellij.ide.startup.importSettings.data.SettingsContributor
import com.intellij.ide.startup.importSettings.data.SettingsService
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.ui.DialogWrapper
import com.jetbrains.rd.util.lifetime.Lifetime
import org.jetbrains.annotations.Nls
import javax.swing.JButton

interface ImportSettingsController {
  companion object {
    fun createController(dialog: OnboardingDialog): ImportSettingsController {
      return ImportSettingsControllerImpl(dialog)
    }
  }

  fun goToSettingsPage(provider: ActionsDataProvider<*>, product: SettingsContributor)
  fun goToProductChooserPage()
  fun goToImportPage(importFromProduct: DialogImportData)
  fun createButton(name: @Nls String, handler: () -> Unit): JButton
  fun createDefaultButton(name: @Nls String, handler: () -> Unit): JButton

  fun skipImport()

  val lifetime: Lifetime
}

private class ImportSettingsControllerImpl(val dialog: OnboardingDialog) : ImportSettingsController {
  override val lifetime: Lifetime = dialog.disposable.createLifetime()
  init {
    val settService = SettingsService.getInstance()
    settService.doClose.advise(lifetime) {
      skipImport()
    }

    settService.error.advise(lifetime) {
      dialog.showError(it)
    }
  }

  override fun goToSettingsPage(provider: ActionsDataProvider<*>, product: SettingsContributor) {
    val page = SettingChooserPage.createPage(provider, product, this)
    dialog.changePage(page)
  }

  override fun goToProductChooserPage() {
    val page = ProductChooserPage(this)
    dialog.changePage(page)
  }

  override fun goToImportPage(importFromProduct: DialogImportData) {
    val page = ImportProgressPage(importFromProduct, this)
    dialog.changePage(page)
  }

  override fun createButton(@Nls name: String, handler: () -> Unit): JButton {
    return dialog.createButton(name, handler)
  }

  override fun createDefaultButton(@Nls name: String, handler: () -> Unit): JButton {
    return dialog.createDefaultButton(name, handler)
  }

  override fun skipImport() {
    dialog.doClose(DialogWrapper.CANCEL_EXIT_CODE)
  }
}