// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.productChooser

import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.data.SettingsService
import com.intellij.ide.startup.importSettings.data.SyncService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent

class SyncStateAction : ChooseProductActionButton() {
  private val settingsService = SettingsService.getInstance()
  private val syncService = settingsService.getSyncService()

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun actionPerformed(e: AnActionEvent) {
    when (syncService.syncState.value) {
      SyncService.SYNC_STATE.UNLOGGED -> {
        syncService.tryToLogin()
        return
      }
      else -> return
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = !settingsService.isSyncEnabled.value
    if(!e.presentation.isVisible) {
      return
    }

    e.presentation.icon = AllIcons.Actions.Refresh
    e.presentation.isVisible = when (syncService.syncState.value) {
      SyncService.SYNC_STATE.UNLOGGED -> {
        e.presentation.text = ImportSettingsBundle.message("choose.product.log.in.to.setting.sync")
        e.presentation.isEnabled = true
        true
      }
      SyncService.SYNC_STATE.TURNED_OFF -> {
        e.presentation.text = ImportSettingsBundle.message("choose.product.setting.sync.turned.off")
        e.presentation.isEnabled = false
        true
      }

      else -> {
        false
      }
    }
  }
}