// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.chooser.importProgress.ImportProgressPage
import com.intellij.ide.startup.importSettings.chooser.productChooser.ProductChooserPage
import com.intellij.ide.startup.importSettings.chooser.settingChooser.SettingChooserPage
import com.intellij.ide.startup.importSettings.data.*
import com.intellij.ide.startup.importSettings.statistics.ImportSettingsEventsCollector
import com.intellij.ide.startup.importSettings.transfer.TransferableSetting
import com.intellij.ide.startup.importSettings.wizard.pluginChooser.WizardPluginsPage
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.OnboardingBackgroundImageProvider
import com.intellij.openapi.util.Disposer
import com.jetbrains.rd.util.reactive.viewNotNull

internal sealed interface ImportSettingsController : BaseController {
  companion object {
    fun createController(dialog: OnboardingDialog, skipImportAction: () -> Unit): ImportSettingsController {
      return ImportSettingsControllerImpl(dialog, skipImportAction)
    }
  }

  val skipImportAction: () -> Unit

  fun goToSettingsPage(provider: ActionsDataProvider<*>, product: Product)
  fun goToProductChooserPage()

  /**
   * Whether we can show the page in the current configuration at all. E.g., in Rider we can (but only if there are plugins to install),
   * in others we can't.
   */
  fun canShowFeaturedPluginsPage(origin: SettingsImportOrigin): Boolean

  /**
   * Whether we will definitely show the page, given a particular setting configuration.
   * I.e., we will only show the page if the plugins we are going to show aren't in the import list already.
   */
  fun shouldShowFeaturedPluginsPage(
    productId: String,
    dataForSave: List<DataForSave>,
    productService: BaseService,
  ): Boolean

  fun goToFeaturedPluginsPage(
    provider: ActionsDataProvider<*>,
    productService: BaseService,
    product: Product,
    dataForSave: List<DataForSave>
  )
  fun goToProgressPage(importFromProduct: DialogImportData, dataToApply: DataToApply)

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

  override fun goToSettingsPage(provider: ActionsDataProvider<*>, product: Product) {
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

  override fun canShowFeaturedPluginsPage(origin: SettingsImportOrigin): Boolean = when (origin) {
    SettingsImportOrigin.ThirdPartyProduct, SettingsImportOrigin.JetBrainsProduct -> StartupWizardService.getInstance() != null
    SettingsImportOrigin.Sync -> false
  }

  override fun shouldShowFeaturedPluginsPage(
    productId: String,
    dataForSave: List<DataForSave>,
    productService: BaseService
  ): Boolean {
    val pluginService = StartupWizardService.getInstance()?.getPluginService() ?: run {
      logger.info("No wizard service registered, not going to show the featured plugins page.")
      return false
    }
    val installPlugins = dataForSave.any { it.id == TransferableSetting.PLUGINS_ID }
    val pluginIdsMarkedForInstallation = if (installPlugins) productService.getImportablePluginIds(productId) else emptyList()
    logger.info("${pluginIdsMarkedForInstallation.size} plugins marked for installation so far.")
    if (!pluginService.shouldShowPage(pluginIdsMarkedForInstallation)) {
      logger.info("Plugin service reported that showing the featured plugin page is unnecessary.")
      return false
    }

    logger.info("Going to show the featured plugin installation page.")
    return true
  }

  override fun goToFeaturedPluginsPage(
    provider: ActionsDataProvider<*>,
    productService: BaseService,
    product: Product,
    dataForSave: List<DataForSave>
  ) {
    val wizardService = StartupWizardService.getInstance() ?: error("Cannot find the wizard service.")
    val pluginService = wizardService.getPluginService()
    val page = WizardPluginsPage(this, pluginService, goBackAction = {
      wizardService.onExit()
      goToSettingsPage(provider, product)
    },
    goForwardAction = { featuredPluginIds ->
      val dataToApply = DataToApply(dataForSave, featuredPluginIds)
      val importSettings = productService.importSettings(product.id, dataToApply)
      goToProgressPage(importSettings, dataToApply)
    },
    continueButtonTextOverride = ImportSettingsBundle.message("onboarding.wizard.finish-button"))
    ImportSettingsEventsCollector.featuredPluginsPageShown()
    wizardService.onEnter()
    dialog.changePage(page)
  }

  override fun goToProgressPage(importFromProduct: DialogImportData, dataToApply: DataToApply) {
    val dialogTitleOverride = if (dataToApply.featuredPluginIds.isNotEmpty())
      ImportSettingsBundle.message("onboarding.wizard.getting-ready")
    else null
    val page = ImportProgressPage(importFromProduct, this, dialogTitleOverride)
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

enum class SettingsImportOrigin {
  JetBrainsProduct,
  ThirdPartyProduct,
  Sync
}

private val logger = logger<ImportSettingsController>()