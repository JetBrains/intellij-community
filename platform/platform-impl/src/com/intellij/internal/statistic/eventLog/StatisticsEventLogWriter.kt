// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.text.StringUtil
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.PatternLayout
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

interface StatisticsEventLogWriter {
  fun log(message: String)

  fun getActiveFile(): File?

  fun getFiles(): List<File>

  fun cleanup()

  fun rollOver()
}

class StatisticsEventLogFileWriter(private val recorderId: String, private val maxFileSize: String) : StatisticsEventLogWriter {
  private var fileAppender: StatisticsEventLogFileAppender? = null

  private val eventLogger: Logger = Logger.getLogger("event.logger.$recorderId")

  init {
    eventLogger.level = Level.INFO
    eventLogger.additivity = false

    val pattern = PatternLayout("%m\n")
    try {
      val dir = getEventLogDir()
      fileAppender = StatisticsEventLogFileAppender.create(pattern, dir)
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
    return if (recorderId == "FUS") {
      // don't move FUS logs for backward compatibility
      Paths.get(PathManager.getSystemPath()).resolve("event-log")
    }
    else {
      Paths.get(PathManager.getSystemPath()).resolve("plugins-event-log").resolve(recorderId)
    }
  }

  override fun log(message: String) {
    eventLogger.info(message)
  }

  override fun getActiveFile(): File? {
    val activeLog = fileAppender?.activeLogName ?: return null
    return File(File(getEventLogDir().toUri()), activeLog)
  }

  override fun getFiles(): List<File> {
    val activeLog = fileAppender?.activeLogName
    val files = File(getEventLogDir().toUri()).listFiles { f: File -> !StringUtil.equals(f.name, activeLog) }
    return files?.toList() ?: emptyList()
  }

  override fun cleanup() {
    fileAppender?.cleanUp()
  }

  override fun rollOver() {
    fileAppender?.rollOver()
  }
}