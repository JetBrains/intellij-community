// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.actions

import com.intellij.ide.startup.importSettings.data.SettingsService
import com.intellij.ide.startup.importSettings.data.SyncService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class LoggedSyncAction : DumbAwareAction() {
  private val settingsService = SettingsService.getInstance()
  private val syncService = settingsService.getSyncService()

  override fun actionPerformed(e: AnActionEvent) {
    if(syncService.syncState == SyncService.SYNC_STATE.GENERAL) {
      syncService.generalSync()
      return
    }

    if(syncService.syncState != SyncService.SYNC_STATE.LOGGED) {
      return
    }

    syncService.getMainProduct()?.let{
      syncService.importSettings(it.id)
    }
  }

  override fun update(e: AnActionEvent) {
    if(syncService.syncState == SyncService.SYNC_STATE.GENERAL) {
      e.presentation.text = "Setting Sync"
      e.presentation.isVisible = true
      return
    }

    if(syncService.syncState != SyncService.SYNC_STATE.LOGGED) {
      e.presentation.isVisible = false
      return
    }

    syncService.getMainProduct()?.let{
      e.presentation.text = "${it.name} Setting Sync"
      e.presentation.isVisible = true
      return
    }
    e.presentation.isVisible = false
  }
}