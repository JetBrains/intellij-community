/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.application.ApplicationAdapter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.PatternLayout
import java.io.IOException
import java.nio.file.Paths
import java.util.*

class FeatureUsageFileEventLogger : FeatureUsageEventLogger {
  private val sessionId = UUID.randomUUID().toString().shortedUUID()
  private val bucket = "-1"
  private val recorderVersion = "1"

  private var fileAppender: FeatureUsageEventFileAppender? = null
  private val eventLogger: Logger = Logger.getLogger("feature-usage-event-logger")

  private var lastEvent: LogEvent? = null
  private var lastEventTime: Long = 0
  private var count: Int = 1

  init {
    eventLogger.level = Level.INFO
    eventLogger.additivity = false

    val pattern = PatternLayout("%m\n")
    try {
      val dir = Paths.get(PathManager.getSystemPath()).resolve("event-log")
      fileAppender = FeatureUsageEventFileAppender.create(pattern, dir)
      fileAppender?.let { appender ->
        appender.setMaxFileSize("200KB")
        eventLogger.addAppender(appender)
      }
    }
    catch (e: IOException) {
      System.err.println("Unable to initialize logging for feature usage: " + e.localizedMessage)
    }

    ApplicationManager.getApplication().addApplicationListener(object : ApplicationAdapter() {
      override fun applicationExiting() {
        dispose(eventLogger)
      }
    })
  }

  private fun String.shortedUUID(): String {
    val start = this.lastIndexOf('-')
    if (start > 0 && start + 1 < this.length) {
      return this.substring(start + 1)
    }
    return this
  }

  override fun log(recorderId: String, action: String) {
    log(eventLogger, LogEvent(sessionId, bucket, recorderId, recorderVersion, action))
  }

  private fun log(logger: Logger, event: LogEvent) {
    if (lastEvent != null && event.time - lastEventTime <= 10000 && lastEvent!!.shouldMerge(event)) {
      lastEventTime = event.time
      count++
    }
    else {
      logLastEvent(logger)
      lastEvent = event
      lastEventTime = event.time
    }
  }

  private fun dispose(logger: Logger) {
    log(logger, LogEvent(sessionId, bucket, "feature-usage-stats", recorderVersion, "ideaapp.closed"))
    logLastEvent(logger)
  }

  private fun logLastEvent(logger: Logger) {
    if (lastEvent != null) {
      if (count > 1) {
        lastEvent!!.action.addData("count", count)
      }
      logger.info(LogEventSerializer.toString(lastEvent!!))
    }
    lastEvent = null
    count = 1
  }
}