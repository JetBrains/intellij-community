// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.sync

/*import com.intellij.configurationStore.saveSettings
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.data.ImportError
import com.intellij.ide.startup.importSettings.data.ImportProgress
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.rd.util.withBackgroundContext
import com.intellij.openapi.rd.util.withSyncIOBackgroundContext
import com.intellij.openapi.rd.util.withUiContext*/
//import com.intellij.settingsSync.SettingsSnapshot
//import com.intellij.settingsSync.SettingsSyncMain
//import com.intellij.settingsSync.SettingsSyncRemoteCommunicator
//import com.intellij.settingsSync.UpdateResult
/*
import com.intellij.util.text.nullize
import com.jetbrains.rd.util.reactive.OptProperty
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds
*/

//internal class SettingsSyncProgress : ImportProgress {
//  override val progressMessage = OptProperty<String>()
//  override val progress = OptProperty<Int>()
//  override val error = OptProperty<ImportError>()
//}
//
//private val logger = Logger.getInstance("com.intellij.ide.startup.importSettings.sync.SettingsSyncFlowKt")
//
//internal suspend fun getRemoteSettingsSnapshot(communicator: SettingsSyncRemoteCommunicator): SettingsSnapshot? =
//  logger.runAndLogException {
//    val result = withSyncIOBackgroundContext {
//      communicator.receiveUpdates()
//    }
//    when (result) {
//      is UpdateResult.Success -> result.settingsSnapshot
//      UpdateResult.NoFileOnServer -> null
//      UpdateResult.FileDeletedFromServer -> null
//      is UpdateResult.Error -> {
//        logger.warn("Error from server update: ${result.message}.")
//        null
//      }
//    }
//  }
//
//private val durationForReceiving = 5.seconds
//private const val percentForReceiving = 90
//internal suspend fun performSync(
//  controls: SettingsSyncMain.SettingsSyncControls,
//  progress: SettingsSyncProgress
//) {
//  suspend fun reportProgress(message: String, percent: Int) {
//    withUiContext {
//      progress.progressMessage.set(message)
//      progress.progress.set(percent)
//    }
//  }
//
//  suspend fun reportSuccess() {
//    withUiContext {
//      progress.progressMessage.set("")
//      progress.progress.set(100)
//      // TODO: Reset error?
//    }
//  }
//
//  suspend fun reportError(errorMessage: String?) {
//    withUiContext {
//      val message = errorMessage.nullize(nullizeSpaces = true) ?: ImportSettingsBundle.message("import-settings.sync.unknown-error")
//      progress.error.set(object : ImportError {
//        override val message = ImportSettingsBundle.message("import-settings.sync.general-error", message)
//
//        override fun skip() {
//          TODO("Not yet implemented")
//        }
//
//        override fun tryAgain() {
//          // TODO: Reset the progress here?
//          launch {
//            performSync(controls, progress)
//          }
//        }
//      })
//    }
//  }
//
//  coroutineScope {
//    try {
//      logger.info("Receiving updates from settings sync.")
//      reportProgress(ImportSettingsBundle.message("import-settings.sync.receiving-updates"), 0)
//
//      val resultFlow = async {
//        withBackgroundContext {
//          controls.remoteCommunicator.receiveUpdates()
//        }
//      }
//      val reportingFlow = async { progressOverTime(progress, 100 - percentForReceiving) }
//
//      val result =
//        try {
//          resultFlow.await()
//        } finally {
//          reportingFlow.cancel()
//        }
//
//      when (result) {
//        is UpdateResult.Success ->{
//          progress.progressMessage.set(ImportSettingsBundle.message("import-settings.sync.saving-settings"))
//          saveSettings(ApplicationManager.getApplication(), forceSavingAllSettings = true)
//          reportSuccess()
//        }
//        UpdateResult.NoFileOnServer -> reportError(ImportSettingsBundle.message("import-settings.sync.no-file-on-server"))
//        UpdateResult.FileDeletedFromServer -> reportError(ImportSettingsBundle.message("import-settings.sync.file-deleted-from-server"))
//        is UpdateResult.Error -> reportError(result.message.nullize(nullizeSpaces = true) ?: ImportSettingsBundle.message("import-settings.sync.unknown-error"))
//      }
//
//      if (result is UpdateResult.Success) {
//        saveSettings(ApplicationManager.getApplication(), forceSavingAllSettings = true)
//      }
//    }
//    catch (t: Throwable) {
//      if (t is ProcessCanceledException || t is CancellationException) {
//        logger.info("Settings sync cancelled")
//        progress.progressMessage.set(ImportSettingsBundle.message("import-settings.sync.cancelled"))
//        throw t
//      }
//
//      logger.error(t)
//      reportError(t.localizedMessage)
//    }
//  }
//}
//
//private suspend fun progressOverTime(progress: SettingsSyncProgress, maxPercent: Int) {
//  val durationPerPercent = durationForReceiving / maxPercent
//  for (i in 0 until maxPercent) {
//    withUiContext { progress.progress.set(i) }
//    delay(durationPerPercent)
//  }
//}