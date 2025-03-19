package com.intellij.settingsSync.core.config

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.settingsSync.core.*
import com.intellij.settingsSync.core.communicator.RemoteCommunicatorHolder
import com.intellij.util.EventDispatcher
import java.util.*

class SettingsSyncEnabler {
  companion object {
    private val logger = logger<SettingsSyncEnabler>()
  }
  private val eventDispatcher = EventDispatcher.create(Listener::class.java)


  object State {
    val CANCELLED = UpdateResult.Error("Cancelled")
  }

  fun checkServerStateAsync() {
    eventDispatcher.multicaster.serverStateCheckStarted()
    val communicator = RemoteCommunicatorHolder.getRemoteCommunicator() ?: run {
      logger.info("communicator doesn't exist, skipping check")
      return
    }
    object : Task.Modal(null, SettingsSyncBundle.message("enable.sync.check.server.data.progress"), true) {
      private lateinit var updateResult: UpdateResult

      override fun run(indicator: ProgressIndicator) {
        updateResult = communicator.receiveUpdates()
      }

      override fun onCancel() {
        updateResult = State.CANCELLED
      }

      override fun onFinished() {
        eventDispatcher.multicaster.serverStateCheckFinished(updateResult)
      }
    }.queue()
  }

  fun getServerState() : UpdateResult {
    val communicator = RemoteCommunicatorHolder.getRemoteCommunicator() ?: run {
      logger.info("communicator doesn't exist, skipping check")
      return State.CANCELLED
    }
    return communicator.receiveUpdates()
  }


  fun getSettingsFromServer(syncSettings: SettingsSyncState? = null) {
    eventDispatcher.multicaster.updateFromServerStarted()
    val settingsSyncControls = SettingsSyncMain.getInstance().controls
    object : Task.Modal(null, SettingsSyncBundle.message("enable.sync.get.from.server.progress"), false) {
      private lateinit var updateResult: UpdateResult

      override fun run(indicator: ProgressIndicator) {
        val remoteCommunicator = RemoteCommunicatorHolder.getRemoteCommunicator() ?: run {
          logger.info("communicator doesn't exist, cannot get settings from server")
          updateResult = UpdateResult.Error("No remote communicator")
          return
        }
        val result = remoteCommunicator.receiveUpdates()
        updateResult = result
        if (result is UpdateResult.Success) {
          val cloudEvent = SyncSettingsEvent.CloudChange(result.settingsSnapshot, result.serverVersionId, syncSettings)

          settingsSyncControls.bridge.initialize(SettingsSyncBridge.InitMode.TakeFromServer(cloudEvent))
        }
      }

      override fun onFinished() {
        eventDispatcher.multicaster.updateFromServerFinished(updateResult)
      }
    }.queue()
  }


  fun pushSettingsToServer() {
    val settingsSyncControls = SettingsSyncMain.getInstance().controls
    settingsSyncControls.bridge.initialize(SettingsSyncBridge.InitMode.PushToServer)
  }


  fun addListener(listener: Listener) {
    eventDispatcher.addListener(listener)
  }

  interface Listener : EventListener {
    fun serverStateCheckStarted() {
      serverRequestStarted()
    }

    fun serverStateCheckFinished(state: UpdateResult) {
      serverRequestFinished()
    }

    fun updateFromServerStarted() {
      serverRequestStarted()
    }

    fun updateFromServerFinished(result: UpdateResult) {
      serverRequestFinished()
    }

    fun serverRequestStarted() {}
    fun serverRequestFinished() {}
  }
}