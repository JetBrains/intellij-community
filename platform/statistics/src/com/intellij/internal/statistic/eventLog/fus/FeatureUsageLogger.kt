// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.fus

import com.intellij.internal.statistic.eventLog.*
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CompletableFuture

/**
 * An entry point class to record in event log an information about feature usages.
 *
 * There are two types of events:
 * 1) Regular events, recorded when they occur, e.g. open project, invoked action;
 * 2) State events, should be recorded regularly by scheduler, e.g. configured libraries/frameworks;
 *
 * Each event might be recorded together with an additional (context) information, e.g. source and shortcut for action.
 *
 * Note: FeatureUsageCollector API use this class under the hood.
 * Therefore, if you record statistic with FeatureUsageCollector API there's no need to record events in event log manually.
 *
 * @see com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
 * @see com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
 * @see com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
 */
object FeatureUsageLogger {
  internal var loggerProvider = getEventLogProvider("FUS")
  @TestOnly internal set

  init {
    if (isEnabled()) {
      initStateEventTrackers()
    }
  }

  /**
   * Records that in a group (e.g. 'dialogs', 'intentions') a new event occurred.
   */
  fun log(group: EventLogGroup, action: String) {
    loggerProvider.logger.logAsync(group, action, false)
  }

  /**
   * Records that in a group (e.g. 'dialogs', 'intentions') a new event occurred.
   * Adds context information to the event, e.g. source and shortcut for an action.
   */
  fun log(group: EventLogGroup, action: String, data: Map<String, Any>) {
    loggerProvider.logger.logAsync(group, action, data, false)
  }

  /**
   * Records a new state event in a group (e.g. 'run.configuration.type').
   */
  fun logState(group: EventLogGroup, action: String): CompletableFuture<Void> {
    return loggerProvider.logger.logAsync(group, action, true)
  }

  /**
   * Records a new state event in a group (e.g. 'run.configuration.type').
   * Adds context information to the event, e.g. if configuration is stored on project or on IDE level.
   */
  fun logState(group: EventLogGroup, action: String, data: Map<String, Any>): CompletableFuture<Void> {
    return loggerProvider.logger.logAsync(group, action, data, true)
  }

  /**
   * use [log] with EventLogGroup instead
   * @deprecated
   */
  fun log(groupId: String, action: String) {
    loggerProvider.logger.logAsync(EventLogGroup(groupId, 1), action, true)
  }

  fun cleanup() {
    loggerProvider.logger.cleanup()
  }

  fun rollOver() {
    loggerProvider.logger.rollOver()
  }

  fun flush() : CompletableFuture<Void> {
    val logger = loggerProvider.logger
    if (logger is StatisticsFileEventLogger) {
      return logger.flush()
    }
    return CompletableFuture.completedFuture(null)
  }

  fun getConfig() : StatisticsEventLoggerProvider {
    return loggerProvider
  }

  fun isEnabled() : Boolean {
    return loggerProvider.logger !is EmptyStatisticsEventLogger
  }

  @JvmStatic
  val configVersion: Int get() = getConfig().version
}
