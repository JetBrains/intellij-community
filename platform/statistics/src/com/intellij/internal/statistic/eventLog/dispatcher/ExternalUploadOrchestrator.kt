// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.dispatcher

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.eventLog.LogSystemCollector
import com.intellij.internal.statistic.eventLog.LogSystemCollector.failedToStartField
import com.intellij.internal.statistic.eventLog.LogSystemCollector.notEnabledLoggerProvidersField
import com.intellij.internal.statistic.eventLog.LogSystemCollector.restartField
import com.intellij.internal.statistic.eventLog.LogSystemCollector.runningFromSourcesField
import com.intellij.internal.statistic.eventLog.LogSystemCollector.sendingOnExitDisabledField
import com.intellij.internal.statistic.eventLog.LogSystemCollector.updateInProgressField
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProviders
import com.intellij.internal.statistic.eventLog.uploader.EventLogExternalUploader
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Internal
object ExternalUploadOrchestrator {
  private val LOG = Logger.getInstance(ExternalUploadOrchestrator::class.java)
  private val triggered = AtomicBoolean(false)

  @Volatile
  private var isRestart: Boolean = false

  fun signalRestart() {
    isRestart = true
  }

  /**
   * Launch the external uploader process for all eligible recorders.
   *
   * Idempotent: invoked from every dispatcher's `postClose` hook but spawns the uploader at most once per IDE shutdown.
   * Mirrors the legacy behaviour of `EventLogApplicationLifecycleListener.appWillBeClosed` so users do not notice the switch.
   */
  fun tryStartExternalUpload() {
    if (!triggered.compareAndSet(false, true)) return

    if (isRestart) {
      LOG.info("Statistics. Don't start external uploader because there is restarted")
      LogSystemCollector.externalUploaderLaunched.log(restartField.with(true))
      return
    }

    if (PluginManagerCore.isRunningFromSources()) {
      LOG.info("Statistics. Don't start external uploader because IDE is running from sources")
      LogSystemCollector.externalUploaderLaunched.log(runningFromSourcesField.with(true))
      return
    }

    if (!isSendingOnExitEnabled()) {
      LOG.info("Statistics. Don't start external uploader because sending on exit is disabled")
      LogSystemCollector.externalUploaderLaunched.log(sendingOnExitDisabledField.with(true))
      return
    }

    val enabledLoggerProviders = getEventLogProviders().filter { it.isSendEnabled() && it.sendLogsOnIdeClose }
    if (enabledLoggerProviders.isEmpty()) {
      LOG.info("Statistics. Don't start external uploader because there are no enabled logger providers")
      LogSystemCollector.externalUploaderLaunched.log(notEnabledLoggerProvidersField.with(true))
      return
    }

    if (isUpdateInProgress()) {
      LOG.info("Statistics. Don't start external uploader because update is in progress")
      LogSystemCollector.externalUploaderLaunched.log(updateInProgressField.with(true))
      return
    }

    try {
      EventLogExternalUploader.startExternalUpload(
        enabledLoggerProviders,
        StatisticsUploadAssistant.isUseTestStatisticsConfig(),
        StatisticsUploadAssistant.isUseTestStatisticsSendEndpoint()
      )
    }
    catch (e: Exception) {
      LOG.error("Statistics. Failed to start external log uploader", e)
      LogSystemCollector.externalUploaderLaunched.log(failedToStartField.with(true))
    }
  }

  // default is true; falling back to `false` here matches the legacy "registry not yet loaded => give up" behaviour
  private fun isSendingOnExitEnabled(): Boolean = Registry.`is`("feature.usage.event.log.send.on.ide.close", false)

  private fun isUpdateInProgress(): Boolean =
    ApplicationInfo.getInstance().build.asString() == PropertiesComponent.getInstance().getValue("ide.self.update.started.for.build")
}
