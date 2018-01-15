/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.application.ApplicationAdapter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.PermanentInstallationID
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.PatternLayout
import org.apache.log4j.RollingFileAppender
import java.io.File
import java.io.IOException
import java.util.*

object FeatureUsageEventLogger {
  private val userId = PermanentInstallationID.get()
  private val sessionId = UUID.randomUUID().toString()
  private val eventLogger = if (ApplicationManager.getApplication().isInternal) createLogger() else null

  private var lastEvent: LogEvent? = null
  private var count: Int = 1

  init {
    ApplicationManager.getApplication().addApplicationListener(object : ApplicationAdapter() {
      override fun applicationExiting() {
        if (eventLogger != null) {
          dispose(eventLogger)
        }
      }
    })
  }

  fun log(recorderId: String, action: String) {
    if (eventLogger != null) {
      log(eventLogger, LogEvent(recorderId, userId, sessionId, action))
    }
  }

  private fun log(logger: Logger, event: LogEvent) {
    if (lastEvent != null && lastEvent!!.shouldMerge(event)) {
      count++
    }
    else {
      logLastEvent(logger)
      lastEvent = event
    }
  }

  private fun dispose(logger: Logger) {
    log(logger, LogEvent("feature-usage-stats", userId, sessionId, "ideaapp.closed"))
    logLastEvent(logger)
  }

  private fun logLastEvent(logger: Logger) {
    if (lastEvent != null) {
      if (count > 1) {
        lastEvent!!.data.put("count", count.toString())
      }
      logger.info(LogEventSerializer.toString(lastEvent!!))
    }
    lastEvent = null
    count = 1
  }

  private fun createLogger(): Logger? {
    val logPath = PathManager.getLogPath()
    val file = File(logPath + "/feature-usage.log")

    val logger = Logger.getLogger("feature-usage-event-logger")
    logger.level = Level.INFO
    logger.additivity = false

    val pattern = PatternLayout("%m\n")
    try {
      val fileAppender = RollingFileAppender(pattern, file.absolutePath)
      fileAppender.setMaxFileSize("1MB")
      fileAppender.maxBackupIndex = 10
      logger.addAppender(fileAppender)
    }
    catch (e: IOException) {
      System.err.println("Unable to initialize logging for feature usage: " + e.localizedMessage)
    }
    return logger
  }
}
