// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.settingChooser

import com.intellij.ide.startup.importSettings.data.*
import com.intellij.openapi.ui.DialogWrapper
import java.awt.event.ActionEvent
import javax.swing.Action

fun createDialog(provider: ActionsDataProvider<*>, product: SettingsContributor): DialogWrapper {
  if(provider is SyncActionsDataProvider && provider.productService.baseProduct(product.id)) {
    return SyncSettingDialog(provider, product)
  }
  return ConfigurableSettingChooserDialog(provider, product)
}

class ConfigurableSettingChooserDialog<T : BaseService>(val provider: ActionsDataProvider<T>, product: SettingsContributor) : SettingChooserDialog(provider,
                                                                                                                                                   product) {
  override fun createActions(): Array<Action> {
    return arrayOf(okAction, cancelAction)
  }

  override fun getOKAction(): Action {
    return super.getOKAction().apply {
      putValue(Action.NAME, "Import Settings")
    }
  }

  override fun applyFields() {
    super.applyFields()
    val productService = provider.productService

    val dataForSaves = settingPanes.map { it.item }.filter { it.configurable && it.selected }.map {
      val chs = it.childItems?.filter { it.selected }?.map { it.child.id }?.toList()
      DataForSave(it.setting.id, chs)
    }.toList()
    productService.importSettings(product.id, dataForSaves)
  }

  override fun getCancelAction(): Action {
    return super.getCancelAction().apply {
      putValue(Action.NAME, "Back")
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
    super.applyFields()
    provider.productService.syncSettings()
  }

  private fun getImportAction(): Action {
    return object : DialogWrapperAction("Import Once") {

      override fun doAction(e: ActionEvent?) {
        provider.productService.importSyncSettings()
        close(OK_EXIT_CODE)
      }
    }
  }

  private fun getBackAction(): Action {
    return object : DialogWrapperAction("Back") {

      override fun doAction(e: ActionEvent?) {
        close(CANCEL_EXIT_CODE)
      }
    }
  }
}