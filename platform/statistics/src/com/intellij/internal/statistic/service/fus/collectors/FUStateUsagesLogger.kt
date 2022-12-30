// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "UnstableApiUsage", "ReplacePutWithAssignment")

package com.intellij.internal.statistic.service.fus.collectors

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.EventLogSystemEvents
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProvider
import com.intellij.internal.statistic.eventLog.StatisticsEventLogger
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger.logState
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.future.asDeferred
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.concurrency.asDeferred

/**
 * Called by a scheduler once a day and records IDE/project state. <br></br>
 *
 * **Don't** use it directly unless absolutely necessary.
 * Implement [ApplicationUsagesCollector] or [ProjectUsagesCollector] instead.
 *
 * To record IDE events (e.g. invoked action, opened dialog) use [CounterUsagesCollector]
 */
@Service(Service.Level.APP)
@Internal
class FUStateUsagesLogger private constructor(): UsagesCollectorConsumer {
  // https://github.com/Kotlin/kotlinx.coroutines/issues/2603#issuecomment-808859170
  private val logAppStateRequests = MutableSharedFlow<Boolean?>()
  private val logProjectStateRequests = MutableSharedFlow<Project?>()

  init {
    ApplicationManager.getApplication().coroutineScope.launch {
      logAppStateRequests
        .filterNotNull()
        .collectLatest(::logApplicationStates)
    }
    ApplicationManager.getApplication().coroutineScope.launch {
      logProjectStateRequests
        .filterNotNull()
        .collectLatest { project ->
          coroutineScope {
            val recorderLoggers = HashMap<String, StatisticsEventLogger>()
            for (usagesCollector in ProjectUsagesCollector.getExtensions(this@FUStateUsagesLogger)) {
              if (!getPluginInfo(usagesCollector.javaClass).isDevelopedByJetBrains()) {
                @Suppress("removal", "DEPRECATION")
                LOG.warn("Skip '${usagesCollector.groupId}' because its registered in a third-party plugin")
                continue
              }

              launch {
                val metrics = usagesCollector.getMetrics(project, null)
                logMetricsOrError(project = project,
                                  recorderLoggers = recorderLoggers,
                                  usagesCollector = usagesCollector,
                                  metrics = metrics.asDeferred().await() ?: emptySet())
              }
            }
          }
        }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(FUStateUsagesLogger::class.java)

    fun getInstance(): FUStateUsagesLogger = ApplicationManager.getApplication().getService(FUStateUsagesLogger::class.java)

    private suspend fun logMetricsOrError(project: Project?,
                                          recorderLoggers: MutableMap<String, StatisticsEventLogger>,
                                          usagesCollector: FeatureUsagesCollector,
                                          metrics: Set<MetricEvent>) {
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
        logUsagesAsStateEvents(project = project, group = group, metrics = metrics, logger = logger)
      }
      catch (e: Throwable) {
        if (project != null && project.isDisposed) {
          return
        }

        val data = FeatureUsageData().addProject(project)
        @Suppress("UnstableApiUsage")
        logger.logAsync(group, EventLogSystemEvents.STATE_COLLECTOR_FAILED, data.build(), true).asDeferred().join()
      }
    }

    private suspend fun logUsagesAsStateEvents(project: Project?,
                                               group: EventLogGroup,
                                               metrics: Set<MetricEvent>,
                                               logger: StatisticsEventLogger) {
      if (project != null && project.isDisposed) {
        return
      }

      coroutineScope {
        if (!metrics.isEmpty()) {
          val groupData = addProject(project)
          for (metric in metrics) {
            val data = mergeWithEventData(groupData, metric.data)
            val eventData = data?.build() ?: emptyMap()
            launch {
              logger.logAsync(group, metric.eventId, eventData, true).asDeferred().join()
            }
          }
        }

        launch {
          logger.logAsync(group, EventLogSystemEvents.STATE_COLLECTOR_INVOKED, FeatureUsageData().addProject(project).build(), true).join()
        }
      }
    }

    private fun addProject(project: Project?): FeatureUsageData? = if (project == null) null else FeatureUsageData().addProject(project)

    fun mergeWithEventData(groupData: FeatureUsageData?, data: FeatureUsageData?): FeatureUsageData? {
      if (data == null) {
        return groupData
      }
      val newData = groupData?.copy() ?: FeatureUsageData()
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
      logState(group, event, data.build())
      logState(group, EventLogSystemEvents.STATE_COLLECTOR_INVOKED)
    }
  }

  suspend fun logProjectStates(project: Project) {
    logProjectStateRequests.emit(project)
    logProjectStateRequests.emit(null)
  }

  suspend fun logApplicationStates() {
    logAppStateRequests.emit(false)
    logAppStateRequests.emit(null)
  }

  suspend fun logApplicationStatesOnStartup() {
    logAppStateRequests.emit(true)
    logAppStateRequests.emit(null)
  }

  private suspend fun logApplicationStates(onStartup: Boolean) {
    coroutineScope {
      val recorderLoggers = HashMap<String, StatisticsEventLogger>()

      val collectors = ApplicationUsagesCollector.getExtensions(this@FUStateUsagesLogger, onStartup)

      for (usagesCollector in collectors) {
        if (!getPluginInfo(usagesCollector.javaClass).isDevelopedByJetBrains()) {
          @Suppress("removal", "DEPRECATION")
          LOG.warn("Skip '${usagesCollector.groupId}' because its registered in a third-party plugin")
          continue
        }

        launch {
          logMetricsOrError(project = null,
                            recorderLoggers = recorderLoggers,
                            usagesCollector = usagesCollector,
                            metrics = usagesCollector.getMetricsAsync())
        }
      }
    }
  }
}