// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.text.StringUtil
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.PatternLayout
import java.io.File
import java.io.IOException
import java.nio.file.Paths

interface FeatureUsageEventWriter {
  fun log(message: String)

  fun getFiles(): List<File>
}

class FeatureUsageLogEventWriter : FeatureUsageEventWriter {
  private var fileAppender: FeatureUsageEventFileAppender? = null

  private val eventLogger: Logger = Logger.getLogger("feature-usage-event-logger")

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
  }

  private fun getEventLogDir() = Paths.get(PathManager.getSystemPath()).resolve("event-log")

  override fun log(message: String) {
    eventLogger.info(message)
  }

  override fun getFiles(): List<File> {
    val activeLog = fileAppender?.activeLogName
    val files = File(getEventLogDir().toUri()).listFiles { f: File -> !StringUtil.equals(f.name, activeLog) }
    return files?.toList() ?: emptyList()
  }
}