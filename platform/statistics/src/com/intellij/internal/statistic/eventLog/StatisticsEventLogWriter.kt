// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.PatternLayout
import java.io.File
import java.io.IOException
import java.nio.file.Path

interface StatisticsEventLogWriter {
  fun log(logEvent: LogEvent)

  fun getActiveFile(): EventLogFile?

  fun getLogFilesProvider(): EventLogFilesProvider

  fun cleanup()

  fun rollOver()
}

class StatisticsEventLogFileWriter(private val recorderId: String,
                                   private val maxFileSize: String,
                                   isEap: Boolean,
                                   prefix: String) : StatisticsEventLogWriter {
  private var fileAppender: StatisticsEventLogFileAppender? = null

  private val eventLogger: Logger = Logger.getLogger("event.logger.$recorderId")

  init {
    eventLogger.level = Level.INFO
    eventLogger.additivity = false

    val pattern = PatternLayout("%m\n")
    try {
      val dir = getEventLogDir()
      fileAppender = StatisticsEventLogFileAppender.create(pattern, dir, prefix, isEap)
      fileAppender?.let { appender ->
        appender.setMaxFileSize(maxFileSize)
        eventLogger.addAppender(appender)
      }
    }
    catch (e: IOException) {
      System.err.println("Unable to initialize logging for feature usage: " + e.localizedMessage)
    }
  }

  private fun getEventLogDir(): Path {
    return EventLogConfiguration.getEventLogDataPath().resolve("logs").resolve(recorderId)
  }

  override fun log(logEvent: LogEvent) {
    eventLogger.info(LogEventSerializer.toString(logEvent))
  }

  override fun getActiveFile(): EventLogFile? {
    val activeLog = fileAppender?.activeLogName ?: return null
    return EventLogFile(File(File(getEventLogDir().toUri()), activeLog))
  }

  override fun getLogFilesProvider(): EventLogFilesProvider {
    return DefaultEventLogFilesProvider(getEventLogDir()) { fileAppender?.activeLogName }
  }

  override fun cleanup() {
    fileAppender?.cleanUp()
  }

  override fun rollOver() {
    fileAppender?.rollOver()
  }
}