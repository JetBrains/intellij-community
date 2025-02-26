package com.intellij.settingsSync.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.util.*

@Service
class SettingsSyncStatusTracker {
  private var lastSyncTime = -1L
  private var state: SyncStatus = SyncStatus.Success

  private val eventDispatcher = EventDispatcher.create(Listener::class.java)

  val currentStatus: SyncStatus
    get() = state

  init {
    SettingsSyncEvents.getInstance().addListener(object: SettingsSyncEventListener {
      override fun settingChanged(event: SyncSettingsEvent) {
        if (event is SyncSettingsEvent.CloudChange) {
          updateOnSuccess()
        }
      }

      override fun enabledStateChanged(syncEnabled: Boolean) {
        logger<SettingsSyncStatusTracker>().info("Settings sync enabled state changed to: $syncEnabled")
        // clear the status
        state = SyncStatus.Success
      }
    })
  }

  companion object {
    fun getInstance(): SettingsSyncStatusTracker = ApplicationManager.getApplication().getService(SettingsSyncStatusTracker::class.java)
  }

  fun updateOnSuccess() {
    if (state is SyncStatus.ActionRequired)
      return
    lastSyncTime = System.currentTimeMillis()
    state = SyncStatus.Success
    eventDispatcher.multicaster.syncStatusChanged()
  }

  fun updateOnError(message: @Nls String) {
    if (state is SyncStatus.ActionRequired)
      return
    lastSyncTime = -1
    state = SyncStatus.Error(message)
    eventDispatcher.multicaster.syncStatusChanged()
  }

  fun setActionRequired(message: @Nls String, actionTitle: @Nls String, action: suspend (Component?) -> Unit) {
    lastSyncTime = -1
    state = SyncStatus.ActionRequired(message, actionTitle, action)
    eventDispatcher.multicaster.syncStatusChanged()
  }

  fun clearActionRequired() {
    if (state is SyncStatus.ActionRequired) {
      state = SyncStatus.Success
      eventDispatcher.multicaster.syncStatusChanged()
    }
  }

  fun isSyncSuccessful() = state == SyncStatus.Success

  fun getLastSyncTime() = lastSyncTime

  fun addListener(listener: Listener) {
    eventDispatcher.addListener(listener)
  }

  fun removeListener(listener: Listener) {
    eventDispatcher.removeListener(listener)
  }

  interface Listener : EventListener {
    fun syncStatusChanged()
  }

  sealed class SyncStatus {
    object Success: SyncStatus()
    class Error(val errorMessage: @Nls String): SyncStatus()

    /**
     * @param message - text message that will be shown in the configurable label
     * @param actionTitle - text to use in the button
     * @param action - action to perform when clicked the button. The action will be performed under EDT
     */
    class ActionRequired(val message: @Nls String,
                         val actionTitle: @Nls String,
                         private val action: suspend(Component?) -> Unit): SyncStatus() {
      suspend fun execute(component: Component?) = action(component)
    }
  }
}