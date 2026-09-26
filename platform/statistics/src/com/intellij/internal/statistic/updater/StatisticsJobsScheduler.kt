// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.updater

import com.intellij.ide.ApplicationActivity
import com.intellij.ide.StatisticsNotificationManager
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProviders
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProvidersHolder
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.eventLog.uploader.EventLogExternalUploader
import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.runBlockingCancellable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@InternalIgnoreDependencyViolation
internal class StatisticsJobsScheduler : ApplicationActivity {
  private val sendJobs = ConcurrentHashMap<String, Job>()

  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute() {
    withContext(Dispatchers.IO) {
      if (ApplicationManager.getApplication().extensionArea.hasExtensionPoint(StatisticsEventLoggerProvider.EP_NAME)) {
        StatisticsEventLoggerProvider.EP_NAME.addExtensionPointListener(this@withContext, object : ExtensionPointListener<StatisticsEventLoggerProvider> {
          override fun extensionAdded(extension: StatisticsEventLoggerProvider, pluginDescriptor: PluginDescriptor) {
            launch {
              launchStatisticsSendJob(extension, this)

              if (extension.isLoggingEnabled()) {
                launchValidationRulesUpdate(extension)
              }
            }
          }

          override fun extensionRemoved(extension: StatisticsEventLoggerProvider, pluginDescriptor: PluginDescriptor) {
            sendJobs.remove(extension.recorderId)?.cancel()
          }
        })
      }

      delay(5.seconds)

      launch {
        delay(5.seconds)

        (ApplicationManager.getApplication() as ComponentManagerEx)
          .getServiceAsyncIfDefined(StatisticsNotificationManager::class.java)
          ?.showNotificationIfNeeded()
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

    val providers = serviceAsync<StatisticsEventLogProvidersHolder>().getEventLogProviders().toList()
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
      val fusClient = IntellijSensitiveDataValidator.getInstance(provider.recorderId).fusClient ?: return@launch
      // PersistentQueue paces itself based on the recorder's sendFrequencyMs (passed at construction time);
      // a single scheduleSend kicks off the SDK's internal periodic loop on the client's own scope.
      fusClient.scheduleSend()
    }
    sendJobs[provider.recorderId] = job
  }
}

private suspend fun runValidationRulesUpdate() {
  val providers = getEventLogProviders()
  for (provider in providers) {
    launchValidationRulesUpdate(provider)
  }
  serviceAsync<StatisticsValidationUpdatedService>().updatedDeferred.complete(Unit)
}

private fun launchValidationRulesUpdate(provider: StatisticsEventLoggerProvider) {
  if (provider.isLoggingEnabled()) {
    // Remote-config + metadata refresh loops. The SDK option/metadata message handlers are wired when the
    // FusClient is built (see FusComponentProvider.createFusComponents), not here.
    IntellijSensitiveDataValidator.getInstance(provider.recorderId).fusClient?.scheduleMetadataUpdate()
  }
}

fun updateValidationRules(): Unit = runBlockingCancellable {
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
  val updatedDeferred: CompletableDeferred<Unit> = CompletableDeferred()
}