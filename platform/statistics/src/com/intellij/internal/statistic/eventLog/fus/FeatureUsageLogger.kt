// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.fus

import com.intellij.internal.statistic.eventLog.*
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CompletableFuture

/**
 * An entry point class to record in event log an information about feature usages.
 *
 * DO NOT use this class directly, implement collectors according to "fus-collectors.md" dev guide.
 *
 * @see com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
 * @see com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
 * @see com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
 */
object FeatureUsageLogger {
  internal var loggerProvider = StatisticsEventLogProviderUtil.getEventLogProvider("FUS")
  @TestOnly internal set

  init {
    if (isEnabled()) {
      initStateEventTrackers()
    }
  }

  /**
   * Records that in a group (e.g. 'dialogs', 'intentions') a new event occurred.
   */
  fun log(group: EventLogGroup, action: String): CompletableFuture<Void> {
    return loggerProvider.logger.logAsync(group, action, false)
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
