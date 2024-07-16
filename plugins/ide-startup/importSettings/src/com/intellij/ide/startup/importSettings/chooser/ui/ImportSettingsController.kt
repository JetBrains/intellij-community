// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.ide.startup.importSettings.chooser.importProgress.ImportProgressPage
import com.intellij.ide.startup.importSettings.chooser.productChooser.ProductChooserPage
import com.intellij.ide.startup.importSettings.chooser.settingChooser.SettingChooserPage
import com.intellij.ide.startup.importSettings.data.ActionsDataProvider
import com.intellij.ide.startup.importSettings.data.DialogImportData
import com.intellij.ide.startup.importSettings.data.SettingsContributor
import com.intellij.ide.startup.importSettings.data.SettingsService
import com.intellij.ide.startup.importSettings.statistics.ImportSettingsEventsCollector
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.ui.OnboardingBackgroundImageProvider
import com.intellij.openapi.util.Disposer
import com.jetbrains.rd.util.reactive.viewNotNull

interface ImportSettingsController : BaseController {
  companion object {
    fun createController(dialog: OnboardingDialog, skipImportAction: () -> Unit): ImportSettingsController {
      return ImportSettingsControllerImpl(dialog, skipImportAction)
    }
  }

  val skipImportAction: () -> Unit

  fun goToSettingsPage(provider: ActionsDataProvider<*>, product: SettingsContributor)
  fun goToProductChooserPage()
  fun goToImportPage(importFromProduct: DialogImportData)

  fun skipImport()

  fun configChosen()

}

private class ImportSettingsControllerImpl(dialog: OnboardingDialog, override val skipImportAction: () -> Unit) : ImportSettingsController, BaseControllerImpl(dialog) {
  init {
    val settService = SettingsService.getInstance()
    settService.doClose.advise(lifetime) {
      /**TODO
       * what should we do here?
       */
      /*skipImportAction.invoke()*/
      dialog.dialogClose()
    }


    settService.notification.viewNotNull(lifetime) { lt, it ->
      dialog.showOverlay(it, lt)
    }
  }

  override fun goToSettingsPage(provider: ActionsDataProvider<*>, product: SettingsContributor) {
    val page = SettingChooserPage.createPage(provider, product, this)
    Disposer.tryRegister(dialog.disposable, page)
    provider.productSelected(product)
    dialog.changePage(page)
  }

  override fun goToProductChooserPage() {
    val isDark = LafManager.getInstance().currentUIThemeLookAndFeel?.isDark ?: true
    val page = ProductChooserPage(this, OnboardingBackgroundImageProvider.getInstance().getImage(isDark))
    Disposer.tryRegister(dialog.disposable, page)
    ImportSettingsEventsCollector.productPageShown()
    dialog.changePage(page)
  }

  override fun goToImportPage(importFromProduct: DialogImportData) {
    val page = ImportProgressPage(importFromProduct, this)
    Disposer.tryRegister(dialog.disposable, page)
    ImportSettingsEventsCollector.importProgressPageShown()
    dialog.changePage(page)
  }

  override fun skipImport() {
    dialog.dialogClose()
  }

  override fun configChosen() {
    SettingsService.getInstance().configChosen()
  }
}