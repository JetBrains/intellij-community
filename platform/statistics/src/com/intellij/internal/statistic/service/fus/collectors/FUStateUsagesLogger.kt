// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "UnstableApiUsage", "ReplacePutWithAssignment")

package com.intellij.internal.statistic.service.fus.collectors

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.EventLogSystemEvents
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProvider
import com.intellij.internal.statistic.eventLog.StatisticsEventLogger
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageStateEventTracker
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger.Companion.logCollectorsMetrics
import com.intellij.internal.statistic.updater.allowExecution
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asDeferred
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.Obsolete
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val LOG_APPLICATION_STATES_INITIAL_DELAY = 10.minutes
private val LOG_APPLICATION_STATES_DELAY = 24.hours
private val LOG_PROJECTS_STATES_INITIAL_DELAY = 5.minutes
private val LOG_PROJECTS_STATES_DELAY = 12.hours
private const val REDUCE_DELAY_FLAG_KEY = "fus.internal.reduce.initial.delay"

private val LOG = logger<FUStateUsagesLogger>()

/**
 * Called by a scheduler once a day and records IDE/project state. <br></br>
 *
 * **Don't** use it directly unless absolutely necessary.
 * Implement [ApplicationUsagesCollector] or [ProjectUsagesCollector] instead.
 *
 * To record IDE events (e.g., invoked action, opened dialog) use [CounterUsagesCollector]
 * @see ProjectFUStateUsagesLogger
 */
@Service(Service.Level.APP)
@Internal
class FUStateUsagesLogger private constructor(coroutineScope: CoroutineScope) : UsagesCollectorConsumer {
  init {
    coroutineScope.launch {
      logApplicationStateRegularly()
    }
  }

  companion object {
    fun getInstance(): FUStateUsagesLogger = ApplicationManager.getApplication().service()

    internal suspend fun <C : FeatureUsagesCollector> logCollectorsMetrics(
      project: Project?,
      getCollectors: () -> Collection<C>,
      collectMetrics: suspend (C) -> Set<MetricEvent>,
    ) {
      coroutineScope {
        if (!StatisticsUploadAssistant.isCollectAllowedOrForced()) {
          return@coroutineScope
        }

        val recorderLoggers = HashMap<String, StatisticsEventLogger>()
        for (usagesCollector in getCollectors()) {
          if (!getPluginInfo(usagesCollector.javaClass).isDevelopedByJetBrains()) {
            @Suppress("removal", "DEPRECATION")
            LOG.warn("Skip '${usagesCollector.groupId}' because its registered in a third-party plugin")
            continue
          }

          launch {
            logMetricsOrError(
              project = project,
              recorderLoggers = recorderLoggers,
              usagesCollector = usagesCollector,
              metrics = { collectMetrics(usagesCollector) },
            )
          }
        }
      }
    }

    private suspend inline fun logMetricsOrError(
      project: Project?,
      recorderLoggers: MutableMap<String, StatisticsEventLogger>,
      usagesCollector: FeatureUsagesCollector,
      metrics: () -> Set<MetricEvent>,
    ) {
      var group = usagesCollector.group
      if (group == null) {
        @Suppress("removal", "DEPRECATION")
        group = EventLogGroup(usagesCollector.groupId, usagesCollector.version)
      }

      val recorder = group.recorder
      var logger = recorderLoggers.get(recorder)
      if (logger == null) {
        logger = getEventLogProvider(recorder).logger
        recorderLoggers.put(recorder, logger)
      }

      try {
        val data = metrics.invoke()

        logUsagesAsStateEvents(project = project, group = group, metrics = data, logger = logger)
      }
      catch (e: Throwable) {
        if (Logger.shouldRethrow(e)) {
          throw e
        }

        if (project != null && project.isDisposed) {
          return
        }

        val data = FeatureUsageData(recorder).addProject(project)
        @Suppress("UnstableApiUsage")
        logger.logAsync(group, EventLogSystemEvents.STATE_COLLECTOR_FAILED, data.build(), true).asDeferred().join()

        LOG.error(e)
      }
    }

    private suspend fun logUsagesAsStateEvents(
      project: Project?,
      group: EventLogGroup,
      metrics: Set<MetricEvent>,
      logger: StatisticsEventLogger,
    ) {
      if (project != null && project.isDisposed) {
        return
      }

      coroutineScope {
        if (!metrics.isEmpty()) {
          val groupData = addProject(project, group.recorder)
          for (metric in metrics) {
            val data = mergeWithEventData(groupData, metric.data)
            val eventData = data?.build() ?: emptyMap()
            launch {
              logger.logAsync(group, metric.eventId, eventData, true).asDeferred().join()
            }
          }
        }

        launch {
          logger.logAsync(group, EventLogSystemEvents.STATE_COLLECTOR_INVOKED, FeatureUsageData(group.recorder).addProject(project).build(), true).join()
        }
      }
    }

    private fun addProject(project: Project?, recorder: String): FeatureUsageData? {
      return if (project == null) null else FeatureUsageData(recorder).addProject(project)
    }

    fun mergeWithEventData(groupData: FeatureUsageData?, data: FeatureUsageData?): FeatureUsageData? {
      if (data == null) {
        return groupData
      }
      val newData = groupData?.copy() ?: FeatureUsageData(recorderId = data.recorderId)
      newData.merge(data, "event_")
      return newData
    }

    /**
     * Low-level API to record IDE/project state.
     * Using it directly is error-prone because you'll need to think about metric baseline.
     * **Don't** use it unless absolutely necessary.
     *
     * Consider using counter events [CounterUsagesCollector] or
     * state events recorded by a scheduler [ApplicationUsagesCollector] or [ProjectUsagesCollector]
     */
    fun logStateEvent(group: EventLogGroup, event: String, data: FeatureUsageData) {
      val usageLogger = FeatureUsageLogger.getInstance()
      usageLogger.logState(group, event, data.build())
      usageLogger.logState(group, EventLogSystemEvents.STATE_COLLECTOR_INVOKED)
    }
  }

  private suspend fun logApplicationStateRegularly() {
    allowExecution.set(true)
    delay(LOG_APPLICATION_STATES_INITIAL_DELAY)
    allowExecution.set(false)
    while (true) {
      logApplicationStates(onStartup = false)
      delay(LOG_APPLICATION_STATES_DELAY)
    }
  }

  suspend fun logApplicationStatesOnStartup() {
    logApplicationStates(onStartup = true)
  }

  internal suspend fun logApplicationStates(onStartup: Boolean) {
    logCollectorsMetrics(project = null,
                         getCollectors = { UsageCollectors.getApplicationCollectors(this@FUStateUsagesLogger, onStartup) },
                         collectMetrics = { it.getMetricsAsync() })
  }
}

@Internal
@Service(Service.Level.PROJECT)
class ProjectFUStateUsagesLogger(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) : UsagesCollectorConsumer {

  init {
    coroutineScope.launch {
      project.waitForSmartMode()
      logProjectStateRegularly()
    }
  }

  private suspend fun logProjectStateRegularly() {
    val reduceInitialDelay = System.getProperty(REDUCE_DELAY_FLAG_KEY).toBoolean()
    if (!reduceInitialDelay) {
      delay(LOG_PROJECTS_STATES_INITIAL_DELAY)
    }
    while (true) {
      logProjectState()
      delay(LOG_PROJECTS_STATES_DELAY)
    }
  }

  private suspend fun logProjectState() {
    logCollectorsMetrics(project = project,
                         getCollectors = { UsageCollectors.getProjectCollectors(this@ProjectFUStateUsagesLogger) },
                         collectMetrics = { it.collect(project) })
  }

  @Obsolete
  fun scheduleLogApplicationAndProjectState(): Job {
    return coroutineScope.launch {
      logApplicationAndProjectState()
    }
  }

  suspend fun logApplicationAndProjectState() {
    coroutineScope {
      launch {
        FUStateUsagesLogger.getInstance().logApplicationStates(onStartup = false)
        logProjectState()
      }

      for (extension in FeatureUsageStateEventTracker.EP_NAME.extensionList) {
        launch {
          extension.reportNow()
        }
      }
    }
  }
}
