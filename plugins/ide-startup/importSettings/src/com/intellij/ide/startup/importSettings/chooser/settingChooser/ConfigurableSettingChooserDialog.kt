// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.settingChooser

import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.chooser.importProgress.ImportProgressDialog
import com.intellij.ide.startup.importSettings.chooser.ui.PageProvider
import com.intellij.ide.startup.importSettings.chooser.ui.WizardPageTracker
import com.intellij.ide.startup.importSettings.data.*
import com.intellij.platform.ide.bootstrap.StartupWizardStage
import java.awt.event.ActionEvent
import javax.swing.Action

fun createDialog(provider: ActionsDataProvider<*>, product: SettingsContributor): PageProvider {
  if (provider is SyncActionsDataProvider && provider.productService.baseProduct(product.id)) {
    return SyncSettingDialog(provider, product)
  }
  return ConfigurableSettingChooserDialog(provider, product)
}

class ConfigurableSettingChooserDialog<T : BaseService>(val provider: ActionsDataProvider<T>,
                                                        product: SettingsContributor) : SettingChooserDialog(provider,
                                                                                                             product) {
  override fun createActions(): Array<Action> {
    return arrayOf(okAction, getBackAction())
  }

  override fun getOKAction(): Action {
    return super.getOKAction().apply {
      putValue(Action.NAME, ImportSettingsBundle.message("import.settings.ok"))
    }
  }

  override fun doOKAction() {
    val productService = provider.productService
    val dataForSaves = prepareDataForSave()
    val importSettings = productService.importSettings(product.id, dataForSaves)
    nextStep(ImportProgressDialog(importSettings))
  }

  override fun changeHandler() {
    val dataForSaves = prepareDataForSave()
    okAction.isEnabled = dataForSaves.isNotEmpty()
  }

  private fun prepareDataForSave(): List<DataForSave> {
    return settingPanes.map { it.item }.filter { it.selected }.map {
      val chs = it.childItems?.filter { item -> item.selected }?.map { item -> item.child.id }?.toList() ?: emptyList()
      DataForSave(it.setting.id, chs)
    }
  }

  override val tracker = WizardPageTracker(StartupWizardStage.SettingsToImportPage)
}

class SyncSettingDialog(val provider: SyncActionsDataProvider, product: SettingsContributor) : SettingChooserDialog(provider, product) {
  override val configurable = false

  override fun createActions(): Array<Action> {
    return arrayOf(okAction, getBackAction(), getImportAction())
  }

  override fun getOKAction(): Action {
    return super.getOKAction().apply {
      putValue(Action.NAME, ImportSettingsBundle.message("import.settings.sync.ok"))
    }
  }

  override fun doOKAction() {
    val syncSettings = provider.productService.syncSettings()
    nextStep(ImportProgressDialog(syncSettings), OK_EXIT_CODE)
  }

  private fun getImportAction(): Action {
    return object : DialogWrapperAction(ImportSettingsBundle.message("import.settings.sync.import.once")) {

      override fun doAction(e: ActionEvent?) {
        val importSyncSettings = provider.productService.importSyncSettings()
        nextStep(ImportProgressDialog(importSyncSettings), OK_EXIT_CODE)
      }
    }
  }

  override val tracker = WizardPageTracker(StartupWizardStage.SettingsToSyncPage)
}