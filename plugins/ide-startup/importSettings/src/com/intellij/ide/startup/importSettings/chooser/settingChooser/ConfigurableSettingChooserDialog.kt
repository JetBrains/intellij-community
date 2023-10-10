// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.settingChooser

import com.intellij.ide.startup.importSettings.chooser.importProgress.ImportProgressDialog
import com.intellij.ide.startup.importSettings.chooser.ui.PageProvider
import com.intellij.ide.startup.importSettings.data.*
import java.awt.event.ActionEvent
import javax.swing.Action

fun createDialog(provider: ActionsDataProvider<*>, product: SettingsContributor): PageProvider {
  if(provider is SyncActionsDataProvider && provider.productService.baseProduct(product.id)) {
    return SyncSettingDialog(provider, product)
  }
  return ConfigurableSettingChooserDialog(provider, product)
}

class ConfigurableSettingChooserDialog<T : BaseService>(val provider: ActionsDataProvider<T>, product: SettingsContributor) : SettingChooserDialog(provider,
                                                                                                                                                   product) {
  override fun createActions(): Array<Action> {
    return arrayOf(okAction, getBackAction())
  }

  override fun getOKAction(): Action {
    return super.getOKAction().apply {
      putValue(Action.NAME, "Import Settings")
    }
  }

  override fun doOKAction() {
    val productService = provider.productService


    val dataForSaves = settingPanes.map { it.item }.filter { it.configurable && it.selected }.map {
      val chs = it.childItems?.filter { it.selected }?.map { it.child.id }?.toList()
      DataForSave(it.setting.id, chs)
    }.toList()

    val importSettings = productService.importSettings(product.id, dataForSaves)
    val dialog = ImportProgressDialog(importSettings)

    parentDialog?.let {
      it.showPage(dialog)
    } ?: run {
      super.doOKAction()

      dialog.isModal = false
      dialog.isResizable = false
      dialog.show()
    }

  }
}

class SyncSettingDialog(val provider: SyncActionsDataProvider, product: SettingsContributor) : SettingChooserDialog(provider, product) {
  override val configurable = false

  override fun createActions(): Array<Action> {
    return arrayOf(okAction, getBackAction(), getImportAction())
  }

  override fun getOKAction(): Action {
    return super.getOKAction().apply {
      putValue(Action.NAME, "Sync Settings")
    }
  }

  override fun applyFields() {
    provider.productService.syncSettings()
    super.applyFields()
  }

  private fun getImportAction(): Action {
    return object : DialogWrapperAction("Import Once") {

      override fun doAction(e: ActionEvent?) {
        provider.productService.importSyncSettings()
        doAction(OK_EXIT_CODE)
      }
    }
  }
}