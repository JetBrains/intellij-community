/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.application.ApplicationAdapter
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.text.StringUtil
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.PatternLayout
import java.io.File
import java.io.IOException
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Executors

class FeatureUsageFileEventLogger : FeatureUsageEventLogger {
  private val myLogExecutor = Executors.newSingleThreadExecutor()

  private val sessionId = UUID.randomUUID().toString().shortedUUID()
  private val build = ApplicationInfo.getInstance().build.asBuildNumber()
  private val bucket = "-1"
  private val recorderVersion = "2"

  private var fileAppender: FeatureUsageEventFileAppender? = null
  private val eventLogger: Logger = Logger.getLogger("feature-usage-event-logger")

  private var lastEvent: LogEvent? = null
  private var lastEventTime: Long = 0

  init {
    eventLogger.level = Level.INFO
    eventLogger.additivity = false

    val pattern = PatternLayout("%m\n")
    try {
      val dir = getEventLogDir()
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

  private fun getEventLogDir() = Paths.get(PathManager.getSystemPath()).resolve("event-log")

  private fun String.shortedUUID(): String {
    val start = this.lastIndexOf('-')
    if (start > 0 && start + 1 < this.length) {
      return this.substring(start + 1)
    }
    return this
  }

  private fun BuildNumber.asBuildNumber(): String {
    val str = this.asStringWithoutProductCodeAndSnapshot()
    return if (str.endsWith(".")) str + "0" else str
  }

  override fun log(recorderId: String, action: String, isState: Boolean) {
    log(recorderId, action, Collections.emptyMap(), isState)
  }

  override fun log(recorderId: String, action: String, data: Map<String, Any>, isState: Boolean) {
    myLogExecutor.execute(Runnable {
      val event = LogEvent(sessionId, build, bucket, recorderId, recorderVersion, action, isState)
      for (datum in data) {
        event.event.addData(datum.key, datum.value)
      }
      log(eventLogger, event)
    })
  }

  private fun dispose(logger: Logger) {
    myLogExecutor.execute(Runnable {
      log(logger, LogEvent(sessionId, build, bucket, "lifecycle", recorderVersion, "ideaapp.closed", false))
      logLastEvent(logger)
    })
  }

  private fun log(logger: Logger, event: LogEvent) {
    if (lastEvent != null && event.time - lastEventTime <= 10000 && lastEvent!!.shouldMerge(event)) {
      lastEventTime = event.time
      lastEvent!!.event.increment()
    }
    else {
      logLastEvent(logger)
      lastEvent = event
      lastEventTime = event.time
    }
  }

  private fun logLastEvent(logger: Logger) {
    if (lastEvent != null) {
      logger.info(LogEventSerializer.toString(lastEvent!!))
    }
    lastEvent = null
  }

  override fun getLogFiles() : List<File> {
    val activeLog = fileAppender?.activeLogName
    val files = File(getEventLogDir().toUri()).listFiles({ f: File-> !StringUtil.equals(f.name, activeLog) })
    return files?.toList() ?: emptyList()
  }
}