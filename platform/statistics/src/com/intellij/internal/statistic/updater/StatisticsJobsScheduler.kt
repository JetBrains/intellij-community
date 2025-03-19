// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.updater

import com.intellij.ide.ApplicationActivity
import com.intellij.ide.StatisticsNotificationManager
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProviders
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.eventLog.uploader.EventLogExternalUploader
import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.blockingContext
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@InternalIgnoreDependencyViolation
private class StatisticsJobsScheduler : ApplicationActivity {
  private val sendJobs = ConcurrentHashMap<String, Job>()

  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute() {
    coroutineScope {
      if (ApplicationManager.getApplication().extensionArea.hasExtensionPoint(StatisticsEventLoggerProvider.EP_NAME)) {
        StatisticsEventLoggerProvider.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<StatisticsEventLoggerProvider> {
          override fun extensionAdded(extension: StatisticsEventLoggerProvider, pluginDescriptor: PluginDescriptor) {
            launch {
              launchStatisticsSendJob(extension, this)

              if (extension.isLoggingEnabled()) {
                IntellijSensitiveDataValidator.getInstance(extension.recorderId).update()
              }
            }
          }

          override fun extensionRemoved(extension: StatisticsEventLoggerProvider, pluginDescriptor: PluginDescriptor) {
            sendJobs.remove(extension.recorderId)?.cancel()
          }
        })
      }

      launch {
        delay(10.seconds)

        serviceAsync<StatisticsNotificationManager>().showNotificationIfNeeded()
      }
      launch {
        checkPreviousExternalUploadResult()
      }
      launch {
        runEventLogStatisticsService()
      }
      launch {
        runValidationRulesUpdate()
      }

      // we use `launch` in StatisticsEventLoggerProvider - we need scope
      awaitCancellation()
    }
  }

  private suspend fun runEventLogStatisticsService() {
    delay(1.minutes)

    val providers = getEventLogProviders()
    coroutineScope {
      for (provider in providers) {
        launchStatisticsSendJob(provider, this)
      }
    }
  }

  private fun launchStatisticsSendJob(provider: StatisticsEventLoggerProvider, coroutineScope: CoroutineScope) {
    if (!provider.isSendEnabled()) {
      return
    }

    val job = coroutineScope.launch {
      delay((5 * 60).seconds)

      while (isActive) {
        blockingContext {
          StatisticsUploadAssistant.getEventLogStatisticsService(provider.recorderId).send()
        }
        delay(provider.sendFrequencyMs.milliseconds)
      }
    }
    sendJobs[provider.recorderId] = job
  }
}

private suspend fun runValidationRulesUpdate() {
  if (!System.getProperty("fus.internal.reduce.initial.delay").toBoolean()) {
    delay(3.minutes)
  }

  while (true) {
    updateValidationRules()
    serviceAsync<StatisticsValidationUpdatedService>().updatedDeferred.complete(Unit)

    delay(180.minutes)
  }
}

fun updateValidationRules() {
  val providers = getEventLogProviders()
  for (provider in providers) {
    if (provider.isLoggingEnabled()) {
      IntellijSensitiveDataValidator.getInstance(provider.recorderId).update()
    }
  }
}

private suspend fun checkPreviousExternalUploadResult() {
  delay(3.minutes)
  val providers = getEventLogProviders().filter(StatisticsEventLoggerProvider::sendLogsOnIdeClose)
  EventLogExternalUploader.logPreviousExternalUploadResult(providers)
}


@ApiStatus.Internal
@Service(Service.Level.APP)
class StatisticsValidationUpdatedService {
  val updatedDeferred = CompletableDeferred<Unit>()
}