// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "UnstableApiUsage", "ReplacePutWithAssignment")

package com.intellij.internal.statistic.service.fus.collectors

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.EventLogSystemEvents
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProvider
import com.intellij.internal.statistic.eventLog.StatisticsEventLogger
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger.logState
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageStateEventTracker
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger.Companion.LOG
import com.intellij.internal.statistic.updater.StatisticsStateCollectorsScheduler
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asDeferred
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.concurrency.asDeferred
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val LOG_APPLICATION_STATES_INITIAL_DELAY = 10.minutes
private val LOG_APPLICATION_STATES_DELAY = 24.hours
private val LOG_PROJECTS_STATES_INITIAL_DELAY = 5.minutes
private val LOG_PROJECTS_STATES_DELAY = 12.hours
private const val REDUCE_DELAY_FLAG_KEY = "fus.internal.reduce.initial.delay"

/**
 * Called by a scheduler once a day and records IDE/project state. <br></br>
 *
 * **Don't** use it directly unless absolutely necessary.
 * Implement [ApplicationUsagesCollector] or [ProjectUsagesCollector] instead.
 *
 * To record IDE events (e.g. invoked action, opened dialog) use [CounterUsagesCollector]
 * @see ProjectFUStateUsagesLogger
 */
@Service(Service.Level.APP)
@Internal
class FUStateUsagesLogger private constructor(private val cs: CoroutineScope) : UsagesCollectorConsumer {

  init {
    cs.launch {
      logApplicationStateRegularly()
    }
  }

  companion object {

    internal val LOG = Logger.getInstance(FUStateUsagesLogger::class.java)

    fun getInstance(): FUStateUsagesLogger = ApplicationManager.getApplication().getService(FUStateUsagesLogger::class.java)

    internal suspend fun logMetricsOrError(
      project: Project?,
      recorderLoggers: MutableMap<String, StatisticsEventLogger>,
      usagesCollector: FeatureUsagesCollector,
      metrics: Set<MetricEvent>,
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

  private suspend fun logApplicationStateRegularly() {
    StatisticsStateCollectorsScheduler.allowExecution.set(true)
    delay(LOG_APPLICATION_STATES_INITIAL_DELAY)
    StatisticsStateCollectorsScheduler.allowExecution.set(false)
    while (true) {
      logApplicationStates(onStartup = false)
      delay(LOG_APPLICATION_STATES_DELAY)
    }
  }

  fun scheduleLogApplicationStatesOnStartup(): Job = cs.launch {
    logApplicationStates(onStartup = true)
  }

  fun scheduleLogApplicationState(): Job = cs.launch {
    logApplicationStates(onStartup = false)
  }

  private suspend fun logApplicationStates(onStartup: Boolean) {
    coroutineScope {
      if (!StatisticsUploadAssistant.isCollectAllowedOrForced()) return@coroutineScope

      val recorderLoggers = HashMap<String, StatisticsEventLogger>()

      val collectors = ApplicationUsagesCollector.getExtensions(this@FUStateUsagesLogger, onStartup)

      for (usagesCollector in collectors) {
        if (!getPluginInfo(usagesCollector.javaClass).isDevelopedByJetBrains()) {
          @Suppress("removal", "DEPRECATION")
          LOG.warn("Skip '${usagesCollector.groupId}' because its registered in a third-party plugin")
          continue
        }

        launch {
          logMetricsOrError(
            project = null,
            recorderLoggers = recorderLoggers,
            usagesCollector = usagesCollector,
            metrics = usagesCollector.getMetricsAsync(),
          )
        }
      }
    }
  }

  @ScheduledForRemoval
  @Deprecated(
    message = "Use ProjectFUStateUsagesLogger.scheduleLogProjectState",
    ReplaceWith(
      "project.service<ProjectFUStateUsagesLogger>().scheduleLogProjectState().join()",
      "com.intellij.openapi.components.service"
    ),
  )
  suspend fun logProjectStates(project: Project) {
    project.service<ProjectFUStateUsagesLogger>().scheduleLogProjectState().join()
  }

  @ScheduledForRemoval
  @Deprecated(
    message = "Use FUStateUsagesLogger.scheduleLogProjectState",
    replaceWith = ReplaceWith(
      "scheduleLogApplicationState().join()"
    ),
  )
  suspend fun logApplicationStates() {
    scheduleLogApplicationState().join()
  }
}

@Internal
@Service(Service.Level.PROJECT)
class ProjectFUStateUsagesLogger(
  private val project: Project,
  private val cs: CoroutineScope,
) : UsagesCollectorConsumer {

  init {
    cs.launch {
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

  fun scheduleLogProjectState(): Job = cs.launch {
    logProjectState()
  }

  private suspend fun logProjectState(): Unit = coroutineScope {
    val recorderLoggers = HashMap<String, StatisticsEventLogger>()
    for (usagesCollector in ProjectUsagesCollector.getExtensions(this@ProjectFUStateUsagesLogger)) {
      if (!getPluginInfo(usagesCollector.javaClass).isDevelopedByJetBrains()) {
        @Suppress("removal", "DEPRECATION")
        LOG.warn("Skip '${usagesCollector.groupId}' because its registered in a third-party plugin")
        continue
      }

      launch {
        val metrics = blockingContext { usagesCollector.getMetrics(project, null) }
        FUStateUsagesLogger.logMetricsOrError(
          project = project,
          recorderLoggers = recorderLoggers,
          usagesCollector = usagesCollector,
          metrics = metrics.asDeferred().await() ?: emptySet(),
        )
      }
    }
  }

  fun scheduleLogApplicationAndProjectState(): Deferred<Unit> = cs.async {
    launch {
      FUStateUsagesLogger.getInstance().scheduleLogApplicationState().join()
      logProjectState()
    }

    for (extension in FeatureUsageStateEventTracker.EP_NAME.extensions) {
      launch {
        extension.reportNow()
      }
    }
  }
}
