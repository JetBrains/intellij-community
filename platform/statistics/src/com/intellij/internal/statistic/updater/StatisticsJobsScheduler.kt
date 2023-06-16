// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.updater

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.StatisticsNotificationManager
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProviders
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.eventLog.uploader.EventLogExternalUploader
import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.progress.blockingContext
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@InternalIgnoreDependencyViolation
internal class StatisticsJobsScheduler : ApplicationInitializedListener {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(asyncScope: CoroutineScope) {
    asyncScope.launch {
      val notificationManager = ApplicationManager.getApplication().getService(StatisticsNotificationManager::class.java)
      notificationManager?.showNotificationIfNeeded()
    }
    asyncScope.launch {
      checkPreviousExternalUploadResult()
    }
    asyncScope.launch {
      runEventLogStatisticsService()
    }
    asyncScope.launch {
      runValidationRulesUpdate()
    }
  }
}

private suspend fun runValidationRulesUpdate() {
  if (!System.getProperty("fus.internal.reduce.initial.delay").toBoolean()) {
    delay(3.minutes)
  }

  while (true) {
    val providers = getEventLogProviders()
    for (provider in providers) {
      if (provider.isLoggingEnabled()) {
        blockingContext {
          IntellijSensitiveDataValidator.getInstance(provider.recorderId).update()
        }
      }
    }

    delay(180.minutes)
  }
}

private suspend fun checkPreviousExternalUploadResult() {
  delay(3.minutes)
  val providers = getEventLogProviders().filter(StatisticsEventLoggerProvider::sendLogsOnIdeClose)
  EventLogExternalUploader.logPreviousExternalUploadResult(providers)
}

private suspend fun runEventLogStatisticsService() {
  delay(1.minutes)

  val providers = getEventLogProviders()
  coroutineScope {
    for (provider in providers) {
      if (!provider.isSendEnabled()) {
        continue
      }

      val statisticsService = blockingContext {
        StatisticsUploadAssistant.getEventLogStatisticsService(provider.recorderId)
      }
      launch {
        delay((5 * 60).seconds)

        while (true) {
          statisticsService.send()
          delay(provider.sendFrequencyMs.milliseconds)
        }
      }
    }
  }
}