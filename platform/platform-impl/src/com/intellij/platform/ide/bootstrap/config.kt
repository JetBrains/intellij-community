// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.accessibility.enableScreenReaderSupportIfNecessary
import com.intellij.ide.gdpr.EndUserAgreement
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.ui.ExperimentalUI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.CancellationException

internal suspend fun importConfigIfNeeded(isHeadless: Boolean,
                                          configImportNeededDeferred: Deferred<Boolean>,
                                          lockSystemDirsJob: Job,
                                          logDeferred: Deferred<Logger>,
                                          args: List<String>,
                                          targetDirectoryToImportConfig: Path?,
                                          appStarterDeferred: Deferred<AppStarter>,
                                          euaDocumentDeferred: Deferred<EndUserAgreement.Document?>,
                                          initLafJob: Job): Job? {
  if (isHeadless) {
    importConfigHeadless(configImportNeededDeferred = configImportNeededDeferred,
                         lockSystemDirsJob = lockSystemDirsJob,
                         logDeferred = logDeferred)
    return null
  }

  if (AppMode.isRemoteDevHost() || !configImportNeededDeferred.await()) {
    return null
  }

  initLafJob.join()
  val log = logDeferred.await()
  importConfig(
    args = args,
    targetDirectoryToImportConfig = targetDirectoryToImportConfig ?: PathManager.getConfigDir(),
    log = log,
    appStarter = appStarterDeferred.await(),
    euaDocumentDeferred = euaDocumentDeferred,
  )

  val isNewUser = ConfigImportHelper.isNewUser()
  enableNewUi(logDeferred, isNewUser)
  if (isNewUser) {
    if (isIdeStartupDialogEnabled) {
      log.info("Will enter initial app wizard flow.")
      val result = CompletableDeferred<Boolean>()
      isInitialStart = result
      return result
    }
    else {
      return null
    }
  }
  else {
    return null
  }
}

private suspend fun importConfig(args: List<String>,
                                 targetDirectoryToImportConfig: Path,
                                 log: Logger,
                                 appStarter: AppStarter,
                                 euaDocumentDeferred: Deferred<EndUserAgreement.Document?>) {
  span("screen reader checking") {
    runCatching {
      enableScreenReaderSupportIfNecessary()
    }.getOrLogException(log)
  }

  span("config importing") {
    appStarter.beforeImportConfigs()

    val veryFirstStartOnThisComputer = euaDocumentDeferred.await() != null
    withContext(RawSwingDispatcher) {
      ConfigImportHelper.importConfigsTo(veryFirstStartOnThisComputer, targetDirectoryToImportConfig, args, log)
    }
    appStarter.importFinished(targetDirectoryToImportConfig)
    EarlyAccessRegistryManager.invalidate()
    IconLoader.clearCache()
  }
}

private suspend fun enableNewUi(logDeferred: Deferred<Logger>, isBackgroundSwitch: Boolean = false) {
  try {
    val shouldEnableNewUi = !EarlyAccessRegistryManager.getBoolean("ide.experimental.ui") && !EarlyAccessRegistryManager.getBoolean("moved.to.new.ui")
    if (shouldEnableNewUi) {
      EarlyAccessRegistryManager.setAndFlush(mapOf("ide.experimental.ui" to "true", "moved.to.new.ui" to "true"))
      if (!(isBackgroundSwitch || ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isUnitTestMode)) {
        EarlyAccessRegistryManager.setAndFlush(mapOf(ExperimentalUI.FORCED_SWITCH_TO_NEW_UI to "true"))
      }
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    logDeferred.await().error(e)
  }
}

private suspend fun importConfigHeadless(configImportNeededDeferred: Deferred<Boolean>,
                                         lockSystemDirsJob: Job,
                                         logDeferred: Deferred<Logger>) {
  if (!configImportNeededDeferred.await()) {
    return
  }
  // make sure we lock the dir before writing
  lockSystemDirsJob.join()
  enableNewUi(logDeferred, true)
}
